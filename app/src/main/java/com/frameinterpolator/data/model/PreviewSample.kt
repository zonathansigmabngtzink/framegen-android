package com.frameinterpolator.data.model

data class PreviewSample(
    val originalPreviewPath: String,
    val interpolatedPreviewPath: String,
    val startMs: Long,
    val durationMs: Long,
    val targetFps: Int
)

sealed class PreviewState {
    object Idle : PreviewState()

    object Generating : PreviewState()

    data class Ready(val sample: PreviewSample) : PreviewState()

    data class Error(val message: String) : PreviewState()
}
