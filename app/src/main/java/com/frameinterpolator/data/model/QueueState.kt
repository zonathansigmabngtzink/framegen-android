package com.frameinterpolator.data.model

enum class QueueItemStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Cancelled
}

data class QueuedExportItem(
    val id: String,
    val sourceName: String,
    val targetFps: Int,
    val outputLabel: String,
    val status: QueueItemStatus,
    val detail: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class QueueState(
    val activeItem: QueuedExportItem? = null,
    val pendingItems: List<QueuedExportItem> = emptyList(),
    val completedItems: List<QueuedExportItem> = emptyList(),
    val failedItems: List<QueuedExportItem> = emptyList()
) {
    val totalCount: Int
        get() = pendingItems.size + completedItems.size + failedItems.size + if (activeItem == null) 0 else 1

    val hasPendingWork: Boolean
        get() = activeItem != null || pendingItems.isNotEmpty()
}
