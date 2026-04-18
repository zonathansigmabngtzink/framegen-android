package com.frameinterpolator.data.model

import kotlin.math.roundToInt

object ProcessingOptions {
    private val presetTargetFps = listOf(24, 30, 60, 120)

    fun availableTargetFps(sourceFps: Double): List<Int> {
        return presetTargetFps.filter { it > sourceFps }
    }

    fun isLowSourceFps(sourceFps: Double): Boolean {
        return sourceFps <= 15.0
    }

    fun isValidTargetFps(sourceFps: Double, targetFps: Int): Boolean {
        return targetFps in 1..240 && targetFps.toDouble() > sourceFps
    }

    fun availableOutputSizes(width: Int, height: Int): List<OutputSize> {
        if (width <= 0 || height <= 0) {
            return emptyList()
        }

        val aspectRatio = width.toDouble() / height.toDouble()
        val presets = mutableListOf(
            OutputSize(width, height, "Orijinal")
        )

        listOf(
            "1080p" to 1920,
            "720p" to 1280
        ).forEach { (label, targetWidth) ->
            if (targetWidth < width) {
                presets += OutputSize(
                    width = targetWidth,
                    height = (targetWidth / aspectRatio).roundToInt(),
                    label = label
                )
            }
        }

        return presets.distinctBy { "${it.width}x${it.height}" }
    }
}
