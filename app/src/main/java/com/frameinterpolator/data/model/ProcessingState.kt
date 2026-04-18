package com.frameinterpolator.data.model

import android.net.Uri

sealed class ProcessingState {
    object Idle : ProcessingState()

    object Preparing : ProcessingState()

    data class Running(
        val progress: Float,
        val stageLabel: String,
        val etaMillis: Long? = null
    ) : ProcessingState()

    data class Success(
        val outputUri: Uri,
        val displayName: String
    ) : ProcessingState()

    data class Error(
        val message: String,
        val recoverable: Boolean
    ) : ProcessingState()

    object Cancelled : ProcessingState()
}
