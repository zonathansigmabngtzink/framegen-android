package com.frameinterpolator.data.export

import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportRequest(
    val inputPath: String,
    val outputPath: String,
    val sourceDisplayName: String,
    val sourceDurationMillis: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val config: ProcessingConfig
)

object ExportCommandBuilder {
    fun build(request: ExportRequest): String {
        val filters = mutableListOf<String>()
        if (request.config.outputSize.width != request.sourceWidth ||
            request.config.outputSize.height != request.sourceHeight
        ) {
            filters += "scale=${request.config.outputSize.width}:${request.config.outputSize.height}:flags=lanczos"
        }
        filters += "minterpolate=fps=${request.config.targetFps}"

        return listOf(
            "-y",
            "-i", quote(request.inputPath),
            "-map", "0:v:0",
            "-map", "0:a?",
            "-vf", quote(filters.joinToString(",")),
            "-c:v", "libx264",
            "-preset", request.config.quality.preset,
            "-crf", request.config.quality.crf.toString(),
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "192k",
            "-movflags", "+faststart",
            quote(request.outputPath)
        ).joinToString(" ")
    }

    fun createOutputDisplayName(
        sourceName: String,
        targetFps: Int,
        outputSize: OutputSize,
        interpolationMode: ProcessingConfig.InterpolationMode,
        timestampMillis: Long
    ): String {
        val baseName = sourceName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifEmpty { "frameforge" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
        return "${baseName}_${interpolationMode.shortLabel.lowercase(Locale.US)}_${targetFps}fps_${outputSize.label.lowercase(Locale.US)}_${timestamp}.mp4"
    }

    fun outputRelativePath(): String = Constants.OUTPUT_DIRECTORY

    private fun quote(value: String): String = "'${value.replace("'", "\\'")}'"
}
