package com.frameinterpolator.data.export

import com.frameinterpolator.data.model.ExportDiagnostics
import com.frameinterpolator.data.model.AppPreferences
import com.frameinterpolator.data.model.PreviewState
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.QueueState
import com.frameinterpolator.data.model.VideoMetadata
import kotlinx.coroutines.flow.StateFlow

interface VideoExportRepository {
    val processingState: StateFlow<ProcessingState>
    val queueState: StateFlow<QueueState>
    val previewState: StateFlow<PreviewState>
    val diagnostics: StateFlow<ExportDiagnostics?>

    fun startExport(metadata: VideoMetadata, config: ProcessingConfig, preferences: AppPreferences)

    fun enqueueExports(items: List<Pair<VideoMetadata, ProcessingConfig>>, preferences: AppPreferences)

    fun cancelExport()

    fun clearQueue()

    suspend fun generatePreview(metadata: VideoMetadata, config: ProcessingConfig)

    fun clearPreview()

    fun resetState()

    fun clearDiagnostics()
}
