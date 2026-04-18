package com.frameinterpolator.data.model

data class AppPreferences(
    val defaultQuality: ProcessingConfig.QualityPreset = ProcessingConfig.QualityPreset.MEDIUM,
    val notifyOnCompletion: Boolean = true,
    val rememberLastSelections: Boolean = true,
    val enableDetailedDiagnostics: Boolean = true
)
