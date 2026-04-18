package com.frameinterpolator.presentation.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameinterpolator.data.model.BuiltInPreset
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.VideoMetadata
import com.frameinterpolator.presentation.component.FPSSelectorCard
import com.frameinterpolator.presentation.component.ProcessingCard
import com.frameinterpolator.presentation.component.QualityCard
import com.frameinterpolator.presentation.component.ResolutionCard
import com.frameinterpolator.presentation.component.VideoSelectionCard

@Composable
fun HomeRouteScreen(
    videoMetadata: VideoMetadata?,
    processingState: ProcessingState,
    targetFps: Int?,
    outputLabel: String?,
    quality: ProcessingConfig.QualityPreset,
    interpolationMode: ProcessingConfig.InterpolationMode,
    selectedPreset: BuiltInPreset?,
    estimateLabel: String?,
    availableFps: List<Int>,
    availableSizes: List<OutputSize>,
    showLowFpsWarning: Boolean,
    onSelectVideo: () -> Unit,
    onSelectMultiple: () -> Unit,
    onSetTargetFps: (Int) -> Unit,
    onSetInterpolationMode: (ProcessingConfig.InterpolationMode) -> Unit,
    onApplyCustomFps: (String) -> Boolean,
    validateCustomFps: (String) -> String?,
    onSetOutputSize: (OutputSize) -> Unit,
    onSetQuality: (ProcessingConfig.QualityPreset) -> Unit,
    onStartProcessing: () -> Unit,
    onCancelProcessing: () -> Unit,
    onDismissProcessing: () -> Unit,
    onOpenVideo: (Uri) -> Unit,
    onShareVideo: (Uri) -> Unit
) {
    val exportEnabled = videoMetadata != null &&
        targetFps != null &&
        outputLabel != null &&
        processingState != ProcessingState.Preparing &&
        processingState !is ProcessingState.Running

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroPanel(
                sourceName = videoMetadata?.name,
                targetFps = targetFps,
                mode = interpolationMode,
                outputLabel = outputLabel,
                onSelectVideo = onSelectVideo,
                onSelectMultiple = onSelectMultiple
            )
        }

        item {
            VideoSelectionCard(
                videoMetadata = videoMetadata,
                onSelectVideo = onSelectVideo
            )
        }

        videoMetadata?.let { metadata ->
            item {
                FPSSelectorCard(
                    sourceFps = metadata.fps,
                    showLowFpsWarning = showLowFpsWarning,
                    selectedFps = targetFps,
                    availableFps = availableFps,
                    onFpsSelected = onSetTargetFps,
                    onApplyCustomFps = onApplyCustomFps,
                    validateCustomFps = validateCustomFps
                )
            }
            item {
                ModeSelectorCard(
                    selectedMode = interpolationMode,
                    onModeSelected = onSetInterpolationMode
                )
            }
            item {
                ResolutionCard(
                    sourceWidth = metadata.width,
                    sourceHeight = metadata.height,
                    selectedSize = availableSizes.firstOrNull { it.label == outputLabel } ?: availableSizes.firstOrNull(),
                    availableSizes = availableSizes,
                    onResolutionChanged = onSetOutputSize
                )
            }
            item {
                QualityCard(
                    selectedQuality = quality,
                    onQualityChanged = onSetQuality
                )
            }
        }

        item {
            ExportActionCard(
                targetFps = targetFps,
                outputLabel = outputLabel,
                qualityLabel = quality.label,
                interpolationMode = interpolationMode,
                presetTitle = selectedPreset?.title,
                estimateLabel = estimateLabel,
                enabled = exportEnabled,
                onStartProcessing = onStartProcessing
            )
        }

        item {
            ProcessingCard(
                state = processingState,
                onOpenVideo = onOpenVideo,
                onShareVideo = onShareVideo,
                onCancel = onCancelProcessing,
                onDismiss = onDismissProcessing
            )
        }

        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun HeroPanel(
    sourceName: String?,
    targetFps: Int?,
    mode: ProcessingConfig.InterpolationMode,
    outputLabel: String?,
    onSelectVideo: () -> Unit,
    onSelectMultiple: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = "AI STÜDYO",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = sourceName ?: "Videonu gerçekten akıcı hale getir",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (sourceName == null) {
                            "Bir video seç, AI ya da hızlı modu belirle ve yeni MP4 çıktını al."
                        } else {
                            "Şu an seçili motor: ${mode.label}. Ayarlarını yapıp export'u başlat."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Hedef",
                    value = targetFps?.let { "$it FPS" } ?: "Seçilmedi",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Motor",
                    value = mode.shortLabel,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Çıktı",
                    value = outputLabel ?: "Seçilmedi",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSelectVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Video seç")
                }
                OutlinedButton(
                    onClick = onSelectMultiple,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Toplu seç")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModeSelectorCard(
    selectedMode: ProcessingConfig.InterpolationMode,
    onModeSelected: (ProcessingConfig.InterpolationMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("3. Motor", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProcessingConfig.InterpolationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    text = selectedMode.description,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ExportActionCard(
    targetFps: Int?,
    outputLabel: String?,
    qualityLabel: String,
    interpolationMode: ProcessingConfig.InterpolationMode,
    presetTitle: String?,
    estimateLabel: String?,
    enabled: Boolean,
    onStartProcessing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("6. Export al", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (enabled) {
                    listOfNotNull(
                        interpolationMode.label,
                        targetFps?.let { "$it FPS" },
                        outputLabel,
                        qualityLabel
                    ).joinToString(" • ")
                } else {
                    "Önce kaynak video, hedef FPS ve çıktı boyutunu seç."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (presetTitle != null || estimateLabel != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presetTitle?.let {
                            Text(
                                text = "Hazır ayar: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        estimateLabel?.let {
                            Text(
                                text = "Tahmin: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStartProcessing,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (interpolationMode == ProcessingConfig.InterpolationMode.AI) {
                        "AI export başlat"
                    } else {
                        "Hızlı export başlat"
                    }
                )
            }
        }
    }
}
