package com.booniex.pipes.detect

import android.content.Context
import android.graphics.Bitmap
import com.booniex.pipes.data.PipeBox

/** Real YOLO via ncnn JNI (pipe.param + pipe.bin in assets). */
class NcnnDetector(context: Context) : PipeDetector {
    private val ready: Boolean

    init {
        ready = try {
            YoloNative.nativeInit(context.assets)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        if (!ready) error("NCNN model failed to load (nativeInit=false or .so missing)")
    }

    override fun detect(bitmap: Bitmap): List<PipeBox> {
        val rgba = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Cannot convert bitmap to ARGB_8888")

        val raw = YoloNative.nativeDetect(rgba) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val n = raw[0].toInt().coerceAtLeast(0)
        return buildList(n) {
            for (i in 0 until n) {
                val o = 1 + i * 5
                if (o + 4 >= raw.size) break
                add(
                    PipeBox(
                        x = raw[o].coerceIn(0f, 1f),
                        y = raw[o + 1].coerceIn(0f, 1f),
                        w = raw[o + 2].coerceIn(0f, 1f),
                        h = raw[o + 3].coerceIn(0f, 1f),
                        conf = raw[o + 4],
                    )
                )
            }
        }
    }
}
