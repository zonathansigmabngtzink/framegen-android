package com.frameinterpolator.data.export

import android.content.Context
import androidx.core.content.ContextCompat
import com.frameinterpolator.data.model.AppPreferences
import com.frameinterpolator.data.model.ExportDiagnostics
import com.frameinterpolator.data.model.PreviewState
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.QueueState
import com.frameinterpolator.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class ForegroundVideoExportRepository(
    private val context: Context
) : VideoExportRepository {

    private val previewPipeline by lazy { VideoPreviewPipeline(context) }

    override val processingState: StateFlow<ProcessingState> = ExportSessionStore.processingState
    override val queueState: StateFlow<QueueState> = ExportSessionStore.queueState
    override val previewState: StateFlow<PreviewState> = ExportSessionStore.previewState
    override val diagnostics: StateFlow<ExportDiagnostics?> = ExportSessionStore.diagnostics

    override fun startExport(metadata: VideoMetadata, config: ProcessingConfig, preferences: AppPreferences) {
        enqueueExports(listOf(metadata to config), preferences)
    }

    override fun enqueueExports(items: List<Pair<VideoMetadata, ProcessingConfig>>, preferences: AppPreferences) {
        if (items.isEmpty()) return
        ExportSessionStore.clearCancelRequest()
        ExportSessionStore.updatePreferences(preferences)
        ExportSessionStore.enqueueAll(items)
        ContextCompat.startForegroundService(
            context,
            VideoExportService.createProcessQueueIntent(context)
        )
    }

    override fun cancelExport() {
        context.startService(VideoExportService.createCancelIntent(context))
    }

    override fun clearQueue() {
        ExportSessionStore.clearPendingQueue()
    }

    override suspend fun generatePreview(metadata: VideoMetadata, config: ProcessingConfig) {
        ExportSessionStore.updatePreviewState(PreviewState.Generating)
        withContext(Dispatchers.IO) {
            runCatching {
                previewPipeline.generatePreview(metadata, config)
            }.onSuccess { sample ->
                ExportSessionStore.updatePreviewState(PreviewState.Ready(sample))
            }.onFailure { error ->
                ExportSessionStore.updatePreviewState(
                    PreviewState.Error(error.message ?: "Önizleme oluşturulamadı.")
                )
            }
        }
    }

    override fun clearPreview() {
        previewPipeline.clearPreviewCache()
        ExportSessionStore.clearPreview()
    }

    override fun resetState() {
        val state = processingState.value
        if (state != ProcessingState.Preparing && state !is ProcessingState.Running) {
            ExportSessionStore.resetProcessingState()
        }
    }

    override fun clearDiagnostics() {
        ExportSessionStore.clearDiagnostics()
    }
}
