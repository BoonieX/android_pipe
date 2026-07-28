#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include <string>

#include "ncnn/net.h"
#include "ncnn/mat.h"

#define LOG_TAG "PipeYolo"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static ncnn::Net g_net;
static bool g_loaded = false;
static const int INPUT = 640;
static const float CONF_TH = 0.25f;
static const float NMS_TH = 0.45f;

struct Box {
    float x, y, w, h, conf;
};

static float iou(const Box& a, const Box& b) {
    float x1 = std::max(a.x, b.x);
    float y1 = std::max(a.y, b.y);
    float x2 = std::min(a.x + a.w, b.x + b.w);
    float y2 = std::min(a.y + a.h, b.y + b.h);
    float inter = std::max(0.f, x2 - x1) * std::max(0.f, y2 - y1);
    float uni = a.w * a.h + b.w * b.h - inter;
    return uni > 0.f ? inter / uni : 0.f;
}

static std::vector<Box> nms(std::vector<Box> boxes) {
    std::sort(boxes.begin(), boxes.end(), [](const Box& a, const Box& b) {
        return a.conf > b.conf;
    });
    std::vector<Box> keep;
    std::vector<bool> removed(boxes.size(), false);
    for (size_t i = 0; i < boxes.size(); i++) {
        if (removed[i]) continue;
        keep.push_back(boxes[i]);
        for (size_t j = i + 1; j < boxes.size(); j++) {
            if (!removed[j] && iou(boxes[i], boxes[j]) > NMS_TH)
                removed[j] = true;
        }
    }
    return keep;
}

static bool load_from_assets(AAssetManager* mgr) {
    if (g_loaded) return true;
    g_net.opt.use_vulkan_compute = false;
    g_net.opt.num_threads = 4;
    int r1 = g_net.load_param(mgr, "pipe.param");
    int r2 = g_net.load_model(mgr, "pipe.bin");
    if (r1 != 0 || r2 != 0) {
        LOGE("load failed param=%d model=%d", r1, r2);
        return false;
    }
    g_loaded = true;
    LOGI("model loaded");
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_booniex_pipes_detect_YoloNative_nativeInit(JNIEnv* env, jclass, jobject assetManager) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) return JNI_FALSE;
    return load_from_assets(mgr) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_booniex_pipes_detect_YoloNative_nativeDetect(JNIEnv* env, jclass, jobject bitmap) {
    if (!g_loaded) {
        LOGE("not loaded");
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("bitmap must be RGBA_8888");
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    const int src_w = (int)info.width;
    const int src_h = (int)info.height;

    // letterbox to 640
    float scale = std::min((float)INPUT / src_w, (float)INPUT / src_h);
    int new_w = (int)(src_w * scale);
    int new_h = (int)(src_h * scale);
    int pad_x = (INPUT - new_w) / 2;
    int pad_y = (INPUT - new_h) / 2;

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(
        (const unsigned char*)pixels,
        ncnn::Mat::PIXEL_RGBA2RGB,
        src_w, src_h,
        new_w, new_h
    );
    AndroidBitmap_unlockPixels(env, bitmap);

    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, pad_y, INPUT - new_h - pad_y, pad_x, INPUT - new_w - pad_x,
                           ncnn::BORDER_CONSTANT, 114.f);

    const float norm[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    const float mean[3] = {0.f, 0.f, 0.f};
    in_pad.substract_mean_normalize(mean, norm);

    ncnn::Extractor ex = g_net.create_extractor();
    ex.input("in0", in_pad);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) {
        LOGE("extract failed");
        return nullptr;
    }

    // out: dims=2, w=8400, h=5  (cx,cy,w,h,conf) per candidate
    const int num = out.w;
    const int attrs = out.h;
    if (attrs < 5 || num <= 0) {
        LOGE("bad out shape w=%d h=%d", out.w, out.h);
        return nullptr;
    }

    std::vector<Box> candidates;
    candidates.reserve(256);

    for (int i = 0; i < num; i++) {
        float conf = out.row(4)[i];
        if (conf < CONF_TH) continue;
        float cx = out.row(0)[i];
        float cy = out.row(1)[i];
        float bw = out.row(2)[i];
        float bh = out.row(3)[i];

        // map from letterbox pixels back to original image, then normalize [0,1]
        float x0 = (cx - bw * 0.5f - pad_x) / scale;
        float y0 = (cy - bh * 0.5f - pad_y) / scale;
        float x1 = (cx + bw * 0.5f - pad_x) / scale;
        float y1 = (cy + bh * 0.5f - pad_y) / scale;

        x0 = std::max(0.f, std::min(x0, (float)src_w));
        y0 = std::max(0.f, std::min(y0, (float)src_h));
        x1 = std::max(0.f, std::min(x1, (float)src_w));
        y1 = std::max(0.f, std::min(y1, (float)src_h));

        float nw = (x1 - x0) / src_w;
        float nh = (y1 - y0) / src_h;
        float nx = x0 / src_w;
        float ny = y0 / src_h;
        if (nw <= 0.f || nh <= 0.f) continue;
        candidates.push_back({nx, ny, nw, nh, conf});
    }

    auto kept = nms(candidates);

    // flat: [count, x,y,w,h,conf, ...]
    jfloatArray arr = env->NewFloatArray((jsize)(1 + kept.size() * 5));
    if (!arr) return nullptr;
    std::vector<float> buf(1 + kept.size() * 5);
    buf[0] = (float)kept.size();
    for (size_t i = 0; i < kept.size(); i++) {
        buf[1 + i * 5 + 0] = kept[i].x;
        buf[1 + i * 5 + 1] = kept[i].y;
        buf[1 + i * 5 + 2] = kept[i].w;
        buf[1 + i * 5 + 3] = kept[i].h;
        buf[1 + i * 5 + 4] = kept[i].conf;
    }
    env->SetFloatArrayRegion(arr, 0, (jsize)buf.size(), buf.data());
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_booniex_pipes_detect_YoloNative_nativeRelease(JNIEnv*, jclass) {
    g_net.clear();
    g_loaded = false;
}
