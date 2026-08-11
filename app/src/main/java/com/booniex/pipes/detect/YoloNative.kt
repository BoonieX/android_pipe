package com.booniex.pipes.detect

import android.content.res.AssetManager
import android.graphics.Bitmap

object YoloNative {
    init {
        System.loadLibrary("pipe_yolo")
    }

    external fun nativeInit(assetManager: AssetManager): Boolean

    /** [enhance] = terapkan preprocessing adaptif (gamma/autocontrast) sebelum inferensi. */
    external fun nativeDetect(bitmap: Bitmap, enhance: Boolean): FloatArray?
    external fun nativeRelease()
}
