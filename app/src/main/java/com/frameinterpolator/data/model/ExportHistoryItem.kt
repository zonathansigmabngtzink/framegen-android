package com.frameinterpolator.data.model

import android.net.Uri

data class ExportHistoryItem(
    val uri: Uri,
    val displayName: String,
    val targetFps: Int?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMillis: Long,
    val dateAddedMillis: Long
) {
    val resolutionLabel: String get() = "${width}x${height}"
}
