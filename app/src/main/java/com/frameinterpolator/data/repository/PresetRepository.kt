package com.frameinterpolator.data.repository

import com.frameinterpolator.data.model.BuiltInPreset
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingOptions
import com.frameinterpolator.data.model.VideoMetadata

class PresetRepository {
    fun presets(): List<BuiltInPreset> = BuiltInPreset.entries

    fun resolvePreset(
        preset: BuiltInPreset,
        metadata: VideoMetadata
    ): ProcessingConfig {
        val availableFps = ProcessingOptions.availableTargetFps(metadata.fps)
        val resolvedFps = when {
            preset.preferredFps in availableFps -> preset.preferredFps
            availableFps.isNotEmpty() -> availableFps.max()
            else -> (metadata.fps.toInt() + 1).coerceAtMost(240)
        }

        val availableSizes = ProcessingOptions.availableOutputSizes(metadata.width, metadata.height)
        val resolvedSize = preset.preferredHeight
            ?.let { preferredHeight ->
                availableSizes.firstOrNull { it.height == preferredHeight }
            }
            ?: availableSizes.firstOrNull()
            ?: OutputSize(metadata.width, metadata.height, "Orijinal")

        return ProcessingConfig(
            targetFps = resolvedFps,
            outputSize = resolvedSize,
            quality = preset.preferredQuality
        )
    }
}
