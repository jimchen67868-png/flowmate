package com.example.automateclone.engine

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

object ImageMatcher {

    data class MatchResult(val found: Boolean, val x: Int, val y: Int, val score: Double)

    fun findTemplate(source: Bitmap, template: Bitmap, threshold: Double): MatchResult {
        val scale = (TARGET_TEMPLATE_SIZE.toDouble() / min(template.width, template.height).coerceAtLeast(1))
            .coerceAtMost(1.0)

        val srcW = max(1, (source.width * scale).toInt())
        val srcH = max(1, (source.height * scale).toInt())
        val tmpW = max(1, (template.width * scale).toInt())
        val tmpH = max(1, (template.height * scale).toInt())

        if (tmpW > srcW || tmpH > srcH) {
            return MatchResult(false, 0, 0, 0.0)
        }

        val srcGray = toGrayscale(source, srcW, srcH)
        val tmpGray = toGrayscale(template, tmpW, tmpH)

        var bestScore = -1.0
        var bestX = 0
        var bestY = 0

        val stepX = max(1, srcW / 200)
        val stepY = max(1, srcH / 200)

        var y = 0
        while (y <= srcH - tmpH) {
            var x = 0
            while (x <= srcW - tmpW) {
                val score = matchScoreAt(srcGray, srcW, tmpGray, tmpW, tmpH, x, y)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
                x += stepX
            }
            y += stepY
        }

        val found = bestScore >= threshold
        val centerXScaled = bestX + tmpW / 2
        val centerYScaled = bestY + tmpH / 2
        val realX = (centerXScaled / scale).toInt()
        val realY = (centerYScaled / scale).toInt()

        return MatchResult(found, realX, realY, bestScore)
    }

    private fun toGrayscale(bitmap: Bitmap, w: Int, h: Int): IntArray {
        val scaled = if (bitmap.width != w || bitmap.height != h) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r + g + b) / 3
        }
        if (scaled !== bitmap) scaled.recycle()
        return gray
    }

    private fun matchScoreAt(
        src: IntArray, srcW: Int,
        tmp: IntArray, tmpW: Int, tmpH: Int,
        offsetX: Int, offsetY: Int
    ): Double {
        var diffSum = 0L
        var count = 0
        for (ty in 0 until tmpH) {
            val srcRowStart = (offsetY + ty) * srcW + offsetX
            val tmpRowStart = ty * tmpW
            for (tx in 0 until tmpW) {
                val d = src[srcRowStart + tx] - tmp[tmpRowStart + tx]
                diffSum += if (d < 0) -d else d
                count++
            }
        }
        val avgDiff = diffSum.toDouble() / count
        return 1.0 - (avgDiff / 255.0)
    }

    private const val TARGET_TEMPLATE_SIZE = 40
}
