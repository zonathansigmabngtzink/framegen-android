package com.frameinterpolator.util

import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.VideoMetadata
import kotlin.math.max

data class ExportEstimate(
    val estimatedSizeBytes: Long,
    val estimatedDurationMillis: Long
)

object ExportEstimator {
    fun estimate(
        metadata: VideoMetadata,
        targetFps: Int,
        outputSize: OutputSize,
        quality: ProcessingConfig.QualityPreset
    ): ExportEstimate {
        val frameRatio = targetFps / metadata.fps.coerceAtLeast(1.0)
        val sourcePixels = max(metadata.width * metadata.height, 1)
        val outputPixels = max(outputSize.width * outputSize.height, 1)
        val sizeScale = outputPixels / sourcePixels.toDouble()
        val qualityScale = when (quality) {
            ProcessingConfig.QualityPreset.LOW -> 0.78
            ProcessingConfig.QualityPreset.MEDIUM -> 1.0
            ProcessingConfig.QualityPreset.HIGH -> 1.28
        }
        val estimatedSize = (metadata.size * frameRatio * sizeScale * qualityScale).toLong()
            .coerceAtLeast(metadata.size / 2)
        val estimatedDuration = (metadata.duration * frameRatio * qualityScale).toLong()
            .coerceAtLeast(metadata.duration / 2)

        return ExportEstimate(
            estimatedSizeBytes = estimatedSize,
            estimatedDurationMillis = estimatedDuration
        )
    }
}
