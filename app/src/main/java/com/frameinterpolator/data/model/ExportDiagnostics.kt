package com.frameinterpolator.data.model

data class ExportDiagnostics(
    val stage: String,
    val message: String,
    val ffmpegExitCode: Int? = null,
    val recoverable: Boolean = true,
    val timestampMillis: Long = System.currentTimeMillis()
)
