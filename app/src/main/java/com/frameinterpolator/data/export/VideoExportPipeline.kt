package com.frameinterpolator.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.frameinterpolator.data.local.RIFEProcessor
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.VideoMetadata
import com.frameinterpolator.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class ExportResult(
    val outputUri: Uri,
    val displayName: String
)

class VideoExportPipeline(
    private val context: Context
) {
    private val rifeProcessor by lazy { RIFEProcessor(context) }

    suspend fun export(
        metadata: VideoMetadata,
        config: ProcessingConfig,
        onProgress: (ProcessingState) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        when (config.interpolationMode) {
            ProcessingConfig.InterpolationMode.CLASSIC -> exportClassic(metadata, config, onProgress)
            ProcessingConfig.InterpolationMode.AI -> exportAi(metadata, config, onProgress)
        }
    }

    private suspend fun exportClassic(
        metadata: VideoMetadata,
        config: ProcessingConfig,
        onProgress: (ProcessingState) -> Unit
    ): ExportResult {
        val workDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val inputFile = File(workDir, "source_${System.currentTimeMillis()}_${metadata.name}")
        val tempOutputFile = File(workDir, "output_${System.currentTimeMillis()}.mp4")

        return try {
            onProgress(ProcessingState.Running(0.05f, "Seçilen video kopyalanıyor", null))
            copyInputVideo(metadata, inputFile)

            val command = ExportCommandBuilder.build(
                ExportRequest(
                    inputPath = inputFile.absolutePath,
                    outputPath = tempOutputFile.absolutePath,
                    sourceDisplayName = metadata.name,
                    sourceDurationMillis = metadata.duration,
                    sourceWidth = metadata.width,
                    sourceHeight = metadata.height,
                    config = config
                )
            )

            val session = executeCommandWithStats(
                command = command,
                sourceDurationMillis = metadata.duration,
                stageLabel = "Klasik enterpolasyon ve kodlama",
                onProgress = onProgress
            )
            validateSession(session, "Klasik export alınamadı.")

            onProgress(ProcessingState.Running(0.98f, "Galeriye kaydediliyor", null))
            val displayName = ExportCommandBuilder.createOutputDisplayName(
                sourceName = metadata.name,
                targetFps = config.targetFps,
                outputSize = config.outputSize,
                interpolationMode = config.interpolationMode,
                timestampMillis = System.currentTimeMillis()
            )
            val outputUri = publishToMediaStore(tempOutputFile, displayName)
            ExportResult(outputUri, displayName)
        } finally {
            inputFile.delete()
            tempOutputFile.delete()
        }
    }

    private suspend fun exportAi(
        metadata: VideoMetadata,
        config: ProcessingConfig,
        onProgress: (ProcessingState) -> Unit
    ): ExportResult {
        val workDir = File(context.cacheDir, "ai_export_${System.currentTimeMillis()}").apply { mkdirs() }
        val inputFile = File(workDir, "source_${metadata.name}")
        val tempOutputFile = File(workDir, "output_ai.mp4")
        val sourceFramesDir = File(workDir, "source_frames").apply { mkdirs() }
        val outputFramesDir = File(workDir, "output_frames").apply { mkdirs() }

        return try {
            onProgress(ProcessingState.Running(0.04f, "Seçilen video kopyalanıyor", null))
            copyInputVideo(metadata, inputFile)

            onProgress(ProcessingState.Running(0.08f, "AI modeli hazırlanıyor", null))
            if (!rifeProcessor.ensureInitialized()) {
                throw ExportFailedException("Yapay zeka modeli başlatılamadı.", null)
            }

            onProgress(ProcessingState.Running(0.12f, "Kaynak kareler çıkarılıyor", null))
            extractSourceFrames(inputFile, metadata, config, sourceFramesDir)

            val sourceFrames = sourceFramesDir.listFiles { file ->
                file.isFile && file.extension.equals("jpg", ignoreCase = true)
            }?.sortedBy { it.name } ?: emptyList()

            if (sourceFrames.size < 2) {
                throw ExportFailedException("AI için yeterli kaynak kare çıkarılamadı.", null)
            }

            createAiFrames(
                sourceFrames = sourceFrames,
                metadata = metadata,
                config = config,
                outputFramesDir = outputFramesDir,
                onProgress = onProgress
            )

            onProgress(ProcessingState.Running(0.86f, "AI çıktı videosu kodlanıyor", null))
            encodeAiVideo(
                sourceVideo = inputFile,
                outputFramesDir = outputFramesDir,
                tempOutputFile = tempOutputFile,
                config = config
            )

            onProgress(ProcessingState.Running(0.98f, "Galeriye kaydediliyor", null))
            val displayName = ExportCommandBuilder.createOutputDisplayName(
                sourceName = metadata.name,
                targetFps = config.targetFps,
                outputSize = config.outputSize,
                interpolationMode = config.interpolationMode,
                timestampMillis = System.currentTimeMillis()
            )
            val outputUri = publishToMediaStore(tempOutputFile, displayName)
            ExportResult(outputUri, displayName)
        } finally {
            rifeProcessor.destroy()
            workDir.deleteRecursively()
        }
    }

    private fun copyInputVideo(metadata: VideoMetadata, inputFile: File) {
        context.contentResolver.openInputStream(metadata.uri)?.use { input ->
            inputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Seçilen video açılamadı.")
    }

    private fun extractSourceFrames(
        inputFile: File,
        metadata: VideoMetadata,
        config: ProcessingConfig,
        sourceFramesDir: File
    ) {
        val sourceFps = formatFps(metadata.fps)
        val framesPattern = File(sourceFramesDir, "source_%08d.jpg").absolutePath
        val command = listOf(
            "-y",
            "-i", quote(inputFile.absolutePath),
            "-map", "0:v:0",
            "-vf", quote("scale=${config.outputSize.width}:${config.outputSize.height}:flags=lanczos,fps=$sourceFps"),
            "-qscale:v", jpegQScale(config.quality).toString(),
            quote(framesPattern)
        ).joinToString(" ")

        val session = FFmpegKit.execute(command)
        validateSession(session, "Kaynak kareler çıkarılamadı.")
    }

    private fun createAiFrames(
        sourceFrames: List<File>,
        metadata: VideoMetadata,
        config: ProcessingConfig,
        outputFramesDir: File,
        onProgress: (ProcessingState) -> Unit
    ) {
        val sourceFps = metadata.fps
        val targetFps = config.targetFps.toDouble()
        val lastSourceTime = sourceFrames.lastIndex / sourceFps
        val outputFrameCount = floor(lastSourceTime * targetFps).toInt() + 1
        val startedAt = System.currentTimeMillis()
        val bitmapOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var loadedPairIndex = -1
        var currentBitmap: Bitmap? = null
        var nextBitmap: Bitmap? = null

        fun loadPair(index: Int) {
            if (index == loadedPairIndex) {
                return
            }

            if (index == loadedPairIndex + 1 && nextBitmap != null) {
                currentBitmap?.recycle()
                currentBitmap = nextBitmap
                nextBitmap = BitmapFactory.decodeFile(sourceFrames[index + 1].absolutePath, bitmapOptions)
            } else {
                currentBitmap?.recycle()
                nextBitmap?.recycle()
                currentBitmap = BitmapFactory.decodeFile(sourceFrames[index].absolutePath, bitmapOptions)
                nextBitmap = BitmapFactory.decodeFile(sourceFrames[index + 1].absolutePath, bitmapOptions)
            }

            loadedPairIndex = index
        }

        try {
            for (outputIndex in 0 until outputFrameCount) {
                val outputTime = outputIndex / targetFps
                val sourcePosition = outputTime * sourceFps
                val exactSourceIndex = sourcePosition.roundToInt()
                val outputFile = File(outputFramesDir, "frame_${(outputIndex + 1).toString().padStart(8, '0')}.jpg")

                when {
                    outputIndex == outputFrameCount - 1 -> {
                        sourceFrames.last().copyTo(outputFile, overwrite = true)
                    }

                    exactSourceIndex in sourceFrames.indices && abs(sourcePosition - exactSourceIndex) < 0.0001 -> {
                        sourceFrames[exactSourceIndex].copyTo(outputFile, overwrite = true)
                    }

                    else -> {
                        val sourceIndex = floor(sourcePosition).toInt().coerceIn(0, sourceFrames.lastIndex - 1)
                        loadPair(sourceIndex)
                        val firstFrame = currentBitmap
                            ?: throw ExportFailedException("AI ilk kareyi okuyamadı.", null)
                        val secondFrame = nextBitmap
                            ?: throw ExportFailedException("AI ikinci kareyi okuyamadı.", null)
                        val timestep = (sourcePosition - sourceIndex).toFloat().coerceIn(0f, 1f)
                        val interpolated = rifeProcessor.interpolate(firstFrame, secondFrame, timestep)
                            ?: throw ExportFailedException("AI ara kare oluşturamadı.", null)
                        saveBitmap(interpolated, outputFile, jpegQuality(config.quality))
                        interpolated.recycle()
                    }
                }

                val elapsed = System.currentTimeMillis() - startedAt
                val remaining = if (outputIndex > 0) {
                    val avgPerFrame = elapsed / (outputIndex + 1)
                    avgPerFrame * (outputFrameCount - outputIndex - 1)
                } else {
                    null
                }
                val progress = 0.18f + ((outputIndex + 1) / outputFrameCount.toFloat()) * 0.64f
                onProgress(
                    ProcessingState.Running(
                        progress = progress,
                        stageLabel = "AI kareler üretiliyor",
                        etaMillis = remaining
                    )
                )
            }
        } finally {
            currentBitmap?.recycle()
            nextBitmap?.recycle()
        }
    }

    private fun encodeAiVideo(
        sourceVideo: File,
        outputFramesDir: File,
        tempOutputFile: File,
        config: ProcessingConfig
    ) {
        val inputPattern = File(outputFramesDir, "frame_%08d.jpg").absolutePath
        val command = listOf(
            "-y",
            "-framerate", config.targetFps.toString(),
            "-i", quote(inputPattern),
            "-i", quote(sourceVideo.absolutePath),
            "-map", "0:v:0",
            "-map", "1:a?",
            "-c:v", "libx264",
            "-preset", config.quality.preset,
            "-crf", config.quality.crf.toString(),
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            "-movflags", "+faststart",
            quote(tempOutputFile.absolutePath)
        ).joinToString(" ")

        val session = FFmpegKit.execute(command)
        validateSession(session, "AI çıktı videosu kodlanamadı.")
    }

    private fun saveBitmap(bitmap: Bitmap, outputFile: File, quality: Int) {
        outputFile.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                throw ExportFailedException("AI kare diske yazılamadı.", null)
            }
        }
    }

    private suspend fun executeCommandWithStats(
        command: String,
        sourceDurationMillis: Long,
        stageLabel: String,
        onProgress: (ProcessingState) -> Unit
    ): FFmpegSession = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                if (continuation.isActive) {
                    continuation.resume(completedSession)
                }
            },
            null,
            { statistics ->
                val processedTime = statistics.time.toLong()
                val progress = if (sourceDurationMillis <= 0L) {
                    0.5f
                } else {
                    (processedTime / sourceDurationMillis.toFloat()).coerceIn(0f, 0.95f)
                }
                val eta = if (statistics.speed > 0.0 && sourceDurationMillis > 0L) {
                    (((sourceDurationMillis - processedTime).coerceAtLeast(0L)) / statistics.speed).toLong()
                } else {
                    null
                }
                onProgress(
                    ProcessingState.Running(
                        progress = progress,
                        stageLabel = stageLabel,
                        etaMillis = eta
                    )
                )
            }
        )

        continuation.invokeOnCancellation {
            session.cancel()
        }
    }

    private fun validateSession(session: FFmpegSession, fallbackMessage: String) {
        when {
            ReturnCode.isCancel(session.returnCode) -> throw ExportCancelledException()
            !ReturnCode.isSuccess(session.returnCode) -> {
                val message = session.output?.takeIf { it.isNotBlank() } ?: session.allLogsAsString
                throw ExportFailedException(
                    message = message?.trim().orEmpty().ifEmpty { fallbackMessage },
                    ffmpegExitCode = session.returnCode?.value
                )
            }
        }
    }

    private fun publishToMediaStore(tempOutput: File, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, Constants.OUTPUT_MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, ExportCommandBuilder.outputRelativePath())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Galeri kaydı oluşturulamadı.")

        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempOutput.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Export edilen video yazılamadı.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, finalizeValues, null, null)
            }

            return uri
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun jpegQScale(quality: ProcessingConfig.QualityPreset): Int {
        return when (quality) {
            ProcessingConfig.QualityPreset.LOW -> 4
            ProcessingConfig.QualityPreset.MEDIUM -> 3
            ProcessingConfig.QualityPreset.HIGH -> 2
        }
    }

    private fun jpegQuality(quality: ProcessingConfig.QualityPreset): Int {
        return when (quality) {
            ProcessingConfig.QualityPreset.LOW -> 84
            ProcessingConfig.QualityPreset.MEDIUM -> 90
            ProcessingConfig.QualityPreset.HIGH -> 95
        }
    }

    private fun formatFps(value: Double): String {
        return String.format(Locale.US, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun quote(value: String): String = "'${value.replace("'", "\\'")}'"
}

class ExportCancelledException : RuntimeException("Export iptal edildi")

class ExportFailedException(
    override val message: String,
    val ffmpegExitCode: Int?
) : RuntimeException(message)
