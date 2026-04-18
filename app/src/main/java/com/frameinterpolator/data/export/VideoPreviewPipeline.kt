package com.frameinterpolator.data.export

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.PreviewSample
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.VideoMetadata
import java.io.File
import kotlin.math.roundToInt

class VideoPreviewPipeline(
    private val context: Context
) {
    fun generatePreview(
        metadata: VideoMetadata,
        config: ProcessingConfig
    ): PreviewSample {
        val previewDir = File(context.cacheDir, "preview").apply { mkdirs() }
        val inputFile = File(previewDir, "source_${System.currentTimeMillis()}_${metadata.name}")
        val originalPreview = File(previewDir, "original_preview.mp4")
        val interpolatedPreview = File(previewDir, "interpolated_preview.mp4")

        clearPreviewCache()

        context.contentResolver.openInputStream(metadata.uri)?.use { input ->
            inputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Seçilen video açılamadı.")

        val previewDurationMillis = 3_000L.coerceAtMost(metadata.duration.coerceAtLeast(1_000L))
        val startMs = ((metadata.duration - previewDurationMillis) / 2L).coerceAtLeast(0L)
        val previewSize = resolvePreviewSize(metadata)

        try {
            executeOrThrow(
                listOf(
                    "-y",
                    "-ss", formatSeconds(startMs),
                    "-t", formatSeconds(previewDurationMillis),
                    "-i", quote(inputFile.absolutePath),
                    "-vf", quote("scale=${previewSize.width}:${previewSize.height}:flags=lanczos"),
                    "-an",
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "24",
                    quote(originalPreview.absolutePath)
                ).joinToString(" ")
            )

            executeOrThrow(
                listOf(
                    "-y",
                    "-ss", formatSeconds(startMs),
                    "-t", formatSeconds(previewDurationMillis),
                    "-i", quote(inputFile.absolutePath),
                    "-vf", quote("scale=${previewSize.width}:${previewSize.height}:flags=lanczos,minterpolate=fps=${config.targetFps}"),
                    "-an",
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "24",
                    quote(interpolatedPreview.absolutePath)
                ).joinToString(" ")
            )

            return PreviewSample(
                originalPreviewPath = originalPreview.absolutePath,
                interpolatedPreviewPath = interpolatedPreview.absolutePath,
                startMs = startMs,
                durationMs = previewDurationMillis,
                targetFps = config.targetFps
            )
        } finally {
            inputFile.delete()
        }
    }

    fun clearPreviewCache() {
        File(context.cacheDir, "preview")
            .takeIf(File::exists)
            ?.listFiles()
            ?.forEach(File::delete)
    }

    private fun resolvePreviewSize(metadata: VideoMetadata): OutputSize {
        val maxDimension = 540.0
        val sourceMax = maxOf(metadata.width, metadata.height).coerceAtLeast(1)
        val scale = (maxDimension / sourceMax).coerceAtMost(1.0)
        val width = ((metadata.width * scale).roundToInt() / 2) * 2
        val height = ((metadata.height * scale).roundToInt() / 2) * 2
        return OutputSize(
            width = width.coerceAtLeast(2),
            height = height.coerceAtLeast(2),
            label = "Önizleme"
        )
    }

    private fun executeOrThrow(command: String) {
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw IllegalStateException(
                session.output?.takeIf(String::isNotBlank)
                    ?: session.allLogsAsString
                    ?: "Önizleme oluşturulamadı."
            )
        }
    }

    private fun formatSeconds(milliseconds: Long): String = String.format("%.3f", milliseconds / 1000.0)

    private fun quote(value: String): String = "'${value.replace("'", "\\'")}'"
}
