package com.frameinterpolator.presentation.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameinterpolator.data.model.ProcessingState

@Composable
fun ProcessingCard(
    state: ProcessingState,
    onOpenVideo: (Uri) -> Unit,
    onShareVideo: (Uri) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == ProcessingState.Idle) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ProcessingState.Error -> MaterialTheme.colorScheme.errorContainer
                is ProcessingState.Success -> MaterialTheme.colorScheme.primaryContainer
                is ProcessingState.Cancelled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                ProcessingState.Preparing -> {
                    Text("Export hazırlanıyor", style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export'u iptal et")
                    }
                }

                is ProcessingState.Running -> {
                    Text(state.stageLabel, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "%${(state.progress * 100).toInt()} tamamlandı",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.etaMillis?.let {
                        Text(
                            text = "Tahmini kalan süre: ${formatEta(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export'u iptal et")
                    }
                }

                is ProcessingState.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export tamamlandı", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Movies/FrameForge klasörüne kaydedildi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { onOpenVideo(state.outputUri) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Videoyu aç")
                    }
                    OutlinedButton(onClick = { onShareVideo(state.outputUri) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Videoyu paylaş")
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Kapat")
                    }
                }

                is ProcessingState.Error -> {
                    Text("Export başarısız", style = MaterialTheme.typography.titleLarge)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.recoverable) "Tekrar dene" else "Kapat")
                    }
                }

                ProcessingState.Cancelled -> {
                    Text("Export iptal edildi", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Kapat")
                    }
                }

                else -> Unit
            }
        }
    }
}

private fun formatEta(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}
