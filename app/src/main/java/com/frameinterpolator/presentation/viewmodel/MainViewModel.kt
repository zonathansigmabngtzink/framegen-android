package com.frameinterpolator.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frameinterpolator.data.export.ForegroundVideoExportRepository
import com.frameinterpolator.data.export.VideoExportRepository
import com.frameinterpolator.data.model.AppPreferences
import com.frameinterpolator.data.model.BuiltInPreset
import com.frameinterpolator.data.model.ExportDiagnostics
import com.frameinterpolator.data.model.ExportHistoryItem
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.PreviewState
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingOptions
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.QueueState
import com.frameinterpolator.data.model.VideoMetadata
import com.frameinterpolator.data.repository.AppPreferencesRepository
import com.frameinterpolator.data.repository.ExportHistoryRepository
import com.frameinterpolator.data.repository.PresetRepository
import com.frameinterpolator.data.repository.VideoRepository
import com.frameinterpolator.util.ExportEstimate
import com.frameinterpolator.util.ExportEstimator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val videoRepository = VideoRepository(application)
    private val exportRepository: VideoExportRepository = ForegroundVideoExportRepository(application)
    private val preferencesRepository = AppPreferencesRepository(application)
    private val historyRepository = ExportHistoryRepository(application)
    private val presetRepository = PresetRepository()

    private val _videoMetadata = MutableStateFlow<VideoMetadata?>(null)
    val videoMetadata: StateFlow<VideoMetadata?> = _videoMetadata.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _diagnostics = MutableStateFlow<ExportDiagnostics?>(null)
    val diagnostics: StateFlow<ExportDiagnostics?> = _diagnostics.asStateFlow()

    private val _history = MutableStateFlow<List<ExportHistoryItem>>(emptyList())
    val history: StateFlow<List<ExportHistoryItem>> = _history.asStateFlow()

    private val _preferences = MutableStateFlow(AppPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _targetFps = MutableStateFlow<Int?>(null)
    val targetFps: StateFlow<Int?> = _targetFps.asStateFlow()

    private val _outputSize = MutableStateFlow<OutputSize?>(null)
    val outputSize: StateFlow<OutputSize?> = _outputSize.asStateFlow()

    private val _quality = MutableStateFlow(ProcessingConfig.QualityPreset.MEDIUM)
    val quality: StateFlow<ProcessingConfig.QualityPreset> = _quality.asStateFlow()

    private val _interpolationMode = MutableStateFlow(ProcessingConfig.InterpolationMode.AI)
    val interpolationMode: StateFlow<ProcessingConfig.InterpolationMode> = _interpolationMode.asStateFlow()

    private val _selectedPreset = MutableStateFlow<BuiltInPreset?>(null)
    val selectedPreset: StateFlow<BuiltInPreset?> = _selectedPreset.asStateFlow()

    init {
        viewModelScope.launch {
            exportRepository.processingState.collect { state ->
                _processingState.value = state
                if (state is ProcessingState.Success) {
                    refreshHistory()
                }
            }
        }
        viewModelScope.launch {
            exportRepository.queueState.collect { _queueState.value = it }
        }
        viewModelScope.launch {
            exportRepository.previewState.collect { _previewState.value = it }
        }
        viewModelScope.launch {
            exportRepository.diagnostics.collect { _diagnostics.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _preferences.value = prefs
                if (_videoMetadata.value == null || !prefs.rememberLastSelections) {
                    _quality.value = prefs.defaultQuality
                    if (!prefs.rememberLastSelections) {
                        _interpolationMode.value = ProcessingConfig.InterpolationMode.AI
                    }
                }
            }
        }
        refreshHistory()
    }

    fun builtInPresets(): List<BuiltInPreset> = presetRepository.presets()

    fun onVideoSelected(uri: Uri) {
        exportRepository.resetState()
        exportRepository.clearPreview()
        viewModelScope.launch {
            videoRepository.getVideoMetadata(uri).fold(
                onSuccess = { metadata ->
                    _videoMetadata.value = metadata
                    applyDefaultsForMetadata(metadata)
                    _processingState.value = ProcessingState.Idle
                },
                onFailure = { error ->
                    _videoMetadata.value = null
                    _targetFps.value = null
                    _outputSize.value = null
                    _processingState.value = ProcessingState.Error(
                        message = error.message ?: "Seçilen video okunamadı.",
                        recoverable = true
                    )
                }
            )
        }
    }

    fun queueSelectedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val jobs = uris.map { uri ->
                async {
                    videoRepository.getVideoMetadata(uri).getOrNull()
                }
            }.awaitAll()
                .filterNotNull()
                .map { metadata ->
                    metadata to buildConfigFor(metadata)
                }

            if (jobs.isNotEmpty()) {
                exportRepository.enqueueExports(jobs, _preferences.value)
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _history.value = historyRepository.loadHistory()
        }
    }

    fun getAvailableFps(): List<Int> {
        val sourceFps = _videoMetadata.value?.fps ?: return emptyList()
        return ProcessingOptions.availableTargetFps(sourceFps)
    }

    fun isLowSourceFps(): Boolean {
        val sourceFps = _videoMetadata.value?.fps ?: return false
        return ProcessingOptions.isLowSourceFps(sourceFps)
    }

    fun getAvailableOutputSizes(): List<OutputSize> {
        val metadata = _videoMetadata.value ?: return emptyList()
        return ProcessingOptions.availableOutputSizes(metadata.width, metadata.height)
    }

    fun setTargetFps(fps: Int) {
        val sourceFps = _videoMetadata.value?.fps ?: return
        if (ProcessingOptions.isValidTargetFps(sourceFps, fps)) {
            _targetFps.value = fps
        }
    }

    fun setInterpolationMode(mode: ProcessingConfig.InterpolationMode) {
        _interpolationMode.value = mode
    }

    fun validateCustomTargetFps(input: String): String? {
        val sourceFps = _videoMetadata.value?.fps ?: return "Önce bir video seç."
        val targetFps = input.trim().toIntOrNull() ?: return "Tam sayı gir."

        return when {
            targetFps.toDouble() <= sourceFps -> "Hedef FPS kaynak videodan daha yüksek olmalı."
            targetFps > 240 -> "Hedef FPS en fazla 240 olabilir."
            else -> null
        }
    }

    fun applyCustomTargetFps(input: String): Boolean {
        val error = validateCustomTargetFps(input)
        if (error == null) {
            _targetFps.value = input.trim().toInt()
            return true
        }
        return false
    }

    fun setOutputSize(size: OutputSize) {
        if (getAvailableOutputSizes().any { it.width == size.width && it.height == size.height }) {
            _outputSize.value = size
        }
    }

    fun setQuality(quality: ProcessingConfig.QualityPreset) {
        _quality.value = quality
    }

    fun applyPreset(preset: BuiltInPreset) {
        _selectedPreset.value = preset
        val metadata = _videoMetadata.value ?: return
        val resolved = presetRepository.resolvePreset(preset, metadata)
        _targetFps.value = resolved.targetFps
        _outputSize.value = resolved.outputSize
        _quality.value = resolved.quality
    }

    fun startProcessing() {
        val metadata = _videoMetadata.value ?: return
        exportRepository.startExport(metadata, buildConfigFor(metadata), _preferences.value)
    }

    fun cancelProcessing() {
        exportRepository.cancelExport()
    }

    fun clearQueue() {
        exportRepository.clearQueue()
    }

    fun generatePreview() {
        val metadata = _videoMetadata.value ?: return
        viewModelScope.launch {
            exportRepository.generatePreview(metadata, buildConfigFor(metadata))
        }
    }

    fun clearPreview() {
        exportRepository.clearPreview()
    }

    fun clearDiagnostics() {
        exportRepository.clearDiagnostics()
    }

    fun resetProcessing() {
        exportRepository.resetState()
    }

    fun updateDefaultQuality(quality: ProcessingConfig.QualityPreset) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultQuality(quality)
        }
    }

    fun updateNotifyOnCompletion(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateNotifyOnCompletion(enabled)
        }
    }

    fun updateRememberSelections(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateRememberLastSelections(enabled)
        }
    }

    fun updateDetailedDiagnostics(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateDetailedDiagnostics(enabled)
            if (!enabled) {
                exportRepository.clearDiagnostics()
            }
        }
    }

    fun currentEstimate(): ExportEstimate? {
        val metadata = _videoMetadata.value ?: return null
        val target = _targetFps.value ?: return null
        val size = _outputSize.value ?: return null
        return ExportEstimator.estimate(metadata, target, size, _quality.value)
    }

    private fun applyDefaultsForMetadata(metadata: VideoMetadata) {
        val preset = _selectedPreset.value
        if (preset != null) {
            val resolved = presetRepository.resolvePreset(preset, metadata)
            _targetFps.value = resolved.targetFps
            _outputSize.value = resolved.outputSize
            _quality.value = resolved.quality
            return
        }

        if (_preferences.value.rememberLastSelections) {
            _targetFps.value = _targetFps.value?.takeIf {
                ProcessingOptions.isValidTargetFps(metadata.fps, it)
            } ?: getAvailableFps().firstOrNull()
            _outputSize.value = _outputSize.value?.takeIf { selected ->
                getAvailableOutputSizes().any { it.width == selected.width && it.height == selected.height }
            } ?: getAvailableOutputSizes().firstOrNull()
        } else {
            _targetFps.value = getAvailableFps().firstOrNull()
            _outputSize.value = getAvailableOutputSizes().firstOrNull()
            _quality.value = _preferences.value.defaultQuality
            _interpolationMode.value = ProcessingConfig.InterpolationMode.AI
        }
    }

    private fun buildConfigFor(metadata: VideoMetadata): ProcessingConfig {
        val fallbackSize = getAvailableOutputSizes().firstOrNull()
            ?: OutputSize(metadata.width, metadata.height, "Orijinal")

        return ProcessingConfig(
            targetFps = _targetFps.value
                ?: getAvailableFps().firstOrNull()
                ?: (metadata.fps.toInt() + 1).coerceAtMost(240),
            outputSize = _outputSize.value ?: fallbackSize,
            quality = _quality.value,
            interpolationMode = _interpolationMode.value
        )
    }
}
