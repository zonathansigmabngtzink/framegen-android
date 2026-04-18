package com.frameinterpolator

import com.frameinterpolator.data.export.ExportCommandBuilder
import com.frameinterpolator.data.export.ExportRequest
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingOptionsTest {

    @Test
    fun availableTargetFps_onlyReturnsHigherSafePresets() {
        assertEquals(listOf(24, 30, 60, 120), ProcessingOptions.availableTargetFps(15.0))
        assertEquals(listOf(30, 60, 120), ProcessingOptions.availableTargetFps(24.0))
        assertEquals(listOf(60, 120), ProcessingOptions.availableTargetFps(30.0))
        assertEquals(listOf(60, 120), ProcessingOptions.availableTargetFps(45.0))
        assertEquals(listOf(120), ProcessingOptions.availableTargetFps(60.0))
        assertTrue(ProcessingOptions.availableTargetFps(120.0).isEmpty())
    }

    @Test
    fun lowSourceFpsWarning_and_customValidation_followRules() {
        assertTrue(ProcessingOptions.isLowSourceFps(15.0))
        assertFalse(ProcessingOptions.isLowSourceFps(15.1))
        assertTrue(ProcessingOptions.isValidTargetFps(29.97, 30))
        assertTrue(ProcessingOptions.isValidTargetFps(30.0, 48))
        assertFalse(ProcessingOptions.isValidTargetFps(30.0, 30))
        assertFalse(ProcessingOptions.isValidTargetFps(30.0, 241))
    }

    @Test
    fun availableOutputSizes_neverExceedsSourceResolution() {
        val sizes = ProcessingOptions.availableOutputSizes(1920, 1080)

        assertEquals("Orijinal", sizes.first().label)
        assertTrue(sizes.any { it.label == "720p" })
        assertFalse(sizes.any { it.width > 1920 || it.height > 1080 })
    }

    @Test
    fun exportCommandBuilder_buildsExpectedFfmpegCommand() {
        val request = ExportRequest(
            inputPath = "/data/user/0/source.mp4",
            outputPath = "/data/user/0/output.mp4",
            sourceDisplayName = "sample.mp4",
            sourceDurationMillis = 10_000L,
            sourceWidth = 1920,
            sourceHeight = 1080,
            config = ProcessingConfig(
                targetFps = 60,
                outputSize = OutputSize(1280, 720, "720p"),
                quality = ProcessingConfig.QualityPreset.MEDIUM
            )
        )

        val command = ExportCommandBuilder.build(request)

        assertTrue(command.contains("minterpolate=fps=60"))
        assertTrue(command.contains("scale=1280:720:flags=lanczos"))
        assertTrue(command.contains("-c:a aac"))
        assertTrue(command.contains("-movflags +faststart"))
    }

    @Test
    fun outputDisplayName_isStableAndSanitized() {
        val displayName = ExportCommandBuilder.createOutputDisplayName(
            sourceName = "my holiday clip!.mp4",
            targetFps = 60,
            outputSize = OutputSize(1280, 720, "720p"),
            interpolationMode = ProcessingConfig.InterpolationMode.AI,
            timestampMillis = 1_700_000_000_000L
        )

        assertTrue(displayName.startsWith("my_holiday_clip_ai_60fps_720p_"))
        assertTrue(displayName.endsWith(".mp4"))
    }
}
