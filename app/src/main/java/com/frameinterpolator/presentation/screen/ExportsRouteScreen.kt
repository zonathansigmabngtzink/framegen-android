package com.frameinterpolator.presentation.screen

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameinterpolator.data.model.ExportDiagnostics
import com.frameinterpolator.data.model.ExportHistoryItem
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.data.model.QueueState
import com.frameinterpolator.presentation.component.ProcessingCard
import com.frameinterpolator.util.Formatters

@Composable
fun ExportsRouteScreen(
    queueState: QueueState,
    processingState: ProcessingState,
    diagnostics: ExportDiagnostics?,
    history: List<ExportHistoryItem>,
    onSelectMultiple: () -> Unit,
    onRefreshHistory: () -> Unit,
    onClearQueue: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onCancelExport: () -> Unit,
    onDismissProcessing: () -> Unit,
    onOpenVideo: (Uri) -> Unit,
    onShareVideo: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Çıktılar",
                body = "Aktif export'u, sırayı ve geçmiş kayıtlarını burada gör."
            )
        }
        item {
            QueuePanel(
                queueState = queueState,
                onSelectMultiple = onSelectMultiple,
                onClearQueue = onClearQueue
            )
        }
        item {
            ProcessingCard(
                state = processingState,
                onOpenVideo = onOpenVideo,
                onShareVideo = onShareVideo,
                onCancel = onCancelExport,
                onDismiss = onDismissProcessing
            )
        }
        diagnostics?.let {
            item {
                DiagnosticsPanel(diagnostics = it, onClear = onClearDiagnostics)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Export geçmişi", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onRefreshHistory) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yenile")
                }
            }
        }
        items(history) { export ->
            ExportHistoryCard(export, onOpenVideo, onShareVideo)
        }
        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun QueuePanel(
    queueState: QueueState,
    onSelectMultiple: () -> Unit,
    onClearQueue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sıra", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Toplam ${queueState.totalCount} iş • Bekleyen ${queueState.pendingItems.size} • Tamamlanan ${queueState.completedItems.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSelectMultiple) {
                    Text("Videolar ekle")
                }
                OutlinedButton(
                    onClick = onClearQueue,
                    enabled = queueState.pendingItems.isNotEmpty()
                ) {
                    Text("Bekleyenleri temizle")
                }
            }
            queueState.activeItem?.let { active ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "Aktif: ${active.sourceName} • ${active.targetFps} FPS",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            queueState.pendingItems.take(3).forEach { pending ->
                Text(
                    text = "Bekliyor: ${pending.sourceName} • ${pending.targetFps} FPS • ${pending.outputLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    diagnostics: ExportDiagnostics,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Son hata tanısı", style = MaterialTheme.typography.titleLarge)
            Text("Aşama: ${diagnostics.stage}", style = MaterialTheme.typography.bodySmall)
            diagnostics.ffmpegExitCode?.let {
                Text("FFmpeg çıkış kodu: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(diagnostics.message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onClear) {
                Text("Temizle")
            }
        }
    }
}

@Composable
private fun ExportHistoryCard(
    item: ExportHistoryItem,
    onOpenVideo: (Uri) -> Unit,
    onShareVideo: (Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(
                    item.targetFps?.let { "$it FPS" },
                    item.resolutionLabel,
                    Formatters.formatFileSize(item.sizeBytes),
                    Formatters.formatDuration(item.durationMillis)
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.formatDate(item.dateAddedMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onOpenVideo(item.uri) }) {
                    Text("Aç")
                }
                OutlinedButton(onClick = { onShareVideo(item.uri) }) {
                    Text("Paylaş")
                }
            }
        }
    }
}
