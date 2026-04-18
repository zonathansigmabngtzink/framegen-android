package com.frameinterpolator.data.export

import com.frameinterpolator.data.model.AppPreferences
import com.frameinterpolator.data.model.ExportDiagnostics
import com.frameinterpolator.data.model.PreviewState
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.QueueItemStatus
import com.frameinterpolator.data.model.QueueState
import com.frameinterpolator.data.model.QueuedExportItem
import com.frameinterpolator.data.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class PendingExportJob(
    val id: String,
    val metadata: VideoMetadata,
    val config: ProcessingConfig
)

object ExportSessionStore {
    private val pendingJobs = ArrayDeque<PendingExportJob>()
    private val completedItems = mutableListOf<QueuedExportItem>()
    private val failedItems = mutableListOf<QueuedExportItem>()

    private var activeJob: PendingExportJob? = null
    private var cancelRequested: Boolean = false
    private var currentPreferences: AppPreferences = AppPreferences()

    private val mutableProcessingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = mutableProcessingState.asStateFlow()

    private val mutableQueueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = mutableQueueState.asStateFlow()

    private val mutablePreviewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = mutablePreviewState.asStateFlow()

    private val mutableDiagnostics = MutableStateFlow<ExportDiagnostics?>(null)
    val diagnostics: StateFlow<ExportDiagnostics?> = mutableDiagnostics.asStateFlow()

    fun enqueue(metadata: VideoMetadata, config: ProcessingConfig): String {
        return enqueueAll(listOf(metadata to config)).first()
    }

    fun updatePreferences(preferences: AppPreferences) {
        currentPreferences = preferences
        if (!preferences.enableDetailedDiagnostics) {
            mutableDiagnostics.value = null
        }
    }

    fun preferences(): AppPreferences = currentPreferences

    fun enqueueAll(items: List<Pair<VideoMetadata, ProcessingConfig>>): List<String> {
        val ids = items.map { (metadata, config) ->
            val id = UUID.randomUUID().toString()
            pendingJobs.addLast(PendingExportJob(id, metadata, config))
            id
        }
        refreshQueueState()
        return ids
    }

    internal fun consumeNextJob(): PendingExportJob? {
        if (cancelRequested) {
            return null
        }
        if (activeJob != null) {
            return activeJob
        }

        val nextJob = pendingJobs.removeFirstOrNull() ?: run {
            refreshQueueState()
            return null
        }

        activeJob = nextJob
        refreshQueueState(
            activeOverride = nextJob.asQueueItem(
                status = QueueItemStatus.Running,
                detail = nextJob.config.outputSize.label
            )
        )
        return nextJob
    }

    fun updateProcessingState(state: ProcessingState) {
        mutableProcessingState.value = state
    }

    fun markCompleted(outputUri: android.net.Uri, displayName: String) {
        val completed = activeJob?.asQueueItem(
            status = QueueItemStatus.Completed,
            detail = displayName
        )
        if (completed != null) {
            completedItems.add(0, completed)
        }
        activeJob = null
        mutableProcessingState.value = ProcessingState.Success(outputUri, displayName)
        refreshQueueState()
    }

    fun markFailed(message: String, ffmpegExitCode: Int? = null) {
        val failed = activeJob?.asQueueItem(
            status = QueueItemStatus.Failed,
            detail = message
        )
        if (failed != null) {
            failedItems.add(0, failed)
        }
        mutableDiagnostics.value = ExportDiagnostics(
            stage = "Export",
            message = message,
            ffmpegExitCode = ffmpegExitCode.takeIf { currentPreferences.enableDetailedDiagnostics },
            recoverable = true
        )
        activeJob = null
        mutableProcessingState.value = ProcessingState.Error(message, recoverable = true)
        refreshQueueState()
    }

    fun markCancelled() {
        val cancelled = activeJob?.asQueueItem(
            status = QueueItemStatus.Cancelled,
            detail = "İptal edildi"
        )
        if (cancelled != null) {
            failedItems.add(0, cancelled)
        }
        activeJob = null
        pendingJobs.clear()
        cancelRequested = true
        mutableProcessingState.value = ProcessingState.Cancelled
        refreshQueueState()
    }

    fun clearCancelRequest() {
        cancelRequested = false
    }

    fun clearPendingQueue() {
        pendingJobs.clear()
        refreshQueueState()
    }

    fun resetProcessingState() {
        if (!queueState.value.hasPendingWork) {
            mutableProcessingState.value = ProcessingState.Idle
        }
    }

    fun updatePreviewState(state: PreviewState) {
        mutablePreviewState.value = state
    }

    fun clearPreview() {
        mutablePreviewState.value = PreviewState.Idle
    }

    fun clearDiagnostics() {
        mutableDiagnostics.value = null
    }

    private fun refreshQueueState(activeOverride: QueuedExportItem? = null) {
        mutableQueueState.value = QueueState(
            activeItem = activeOverride ?: activeJob?.asQueueItem(
                status = QueueItemStatus.Running,
                detail = activeJob?.config?.outputSize?.label
            ),
            pendingItems = pendingJobs.map { job ->
                job.asQueueItem(
                    status = QueueItemStatus.Pending,
                    detail = job.config.outputSize.label
                )
            },
            completedItems = completedItems.toList(),
            failedItems = failedItems.toList()
        )
    }

    private fun PendingExportJob.asQueueItem(
        status: QueueItemStatus,
        detail: String?
    ): QueuedExportItem {
        return QueuedExportItem(
            id = id,
            sourceName = metadata.name,
            targetFps = config.targetFps,
            outputLabel = config.outputSize.label,
            status = status,
            detail = detail
        )
    }
}
