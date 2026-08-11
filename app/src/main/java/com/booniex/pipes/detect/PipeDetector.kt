package com.booniex.pipes.detect

import android.graphics.Bitmap
import com.booniex.pipes.data.PipeBox
import kotlin.math.min
import kotlin.random.Random

interface PipeDetector {
    fun detect(bitmap: Bitmap): List<PipeBox>

    /** [enhance] = preprocessing adaptif (gamma/autocontrast) untuk input model (F-21). */
    fun detect(bitmap: Bitmap, enhance: Boolean): List<PipeBox> = detect(bitmap)
}

/**
 * Placeholder until best.pt → NCNN (.param/.bin) is wired via JNI.
 * Produces deterministic-ish grid boxes so UI/flow can be tested offline.
 * // ponytail: replace body with NcnnYoloDetector when model assets land
 */
class StubPipeDetector : PipeDetector {
    override fun detect(bitmap: Bitmap): List<PipeBox> {
        val seed = (bitmap.width * 31L + bitmap.height).toInt()
        val rng = Random(seed)
        val n = 8 + rng.nextInt(12)
        val cols = min(6, (n + 2) / 3)
        val rows = (n + cols - 1) / cols
        val boxW = 0.12f
        val boxH = 0.12f
        val marginX = (1f - cols * boxW) / (cols + 1)
        val marginY = (1f - rows * boxH) / (rows + 1)
        return buildList {
            var i = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (i >= n) break
                    val x = marginX + c * (boxW + marginX) + rng.nextFloat() * 0.02f
                    val y = marginY + r * (boxH + marginY) + rng.nextFloat() * 0.02f
                    add(PipeBox(x.coerceIn(0f, 0.88f), y.coerceIn(0f, 0.88f), boxW, boxH, 0.85f))
                    i++
                }
            }
        }
    }
}
