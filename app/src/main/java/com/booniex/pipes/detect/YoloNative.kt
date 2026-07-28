package com.booniex.pipes.detect

import android.content.res.AssetManager
import android.graphics.Bitmap

object YoloNative {
    init {
        System.loadLibrary("pipe_yolo")
    }

    external fun nativeInit(assetManager: AssetManager): Boolean
    external fun nativeDetect(bitmap: Bitmap): FloatArray?
    external fun nativeRelease()
}
