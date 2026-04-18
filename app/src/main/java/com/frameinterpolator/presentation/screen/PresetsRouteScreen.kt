package com.frameinterpolator.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameinterpolator.data.model.BuiltInPreset

@Composable
fun PresetsRouteScreen(
    presets: List<BuiltInPreset>,
    selectedPreset: BuiltInPreset?,
    sourceName: String?,
    onSelectVideo: () -> Unit,
    onApplyPreset: (BuiltInPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Hazırlar",
                body = sourceName?.let { "Seçili kaynak: $it" }
                    ?: "Bir hazır ayar seçerek FPS, kalite ve boyutu tek dokunuşla kurabilirsin."
            )
        }

        items(presets) { preset ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPreset == preset) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preset.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                preset.tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { onApplyPreset(preset) },
                            label = { Text(if (selectedPreset == preset) "Seçili" else "Uygula") }
                        )
                    }

                    Text(preset.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Hedef ${preset.preferredFps} FPS • Kalite ${preset.preferredQuality.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(onClick = onSelectVideo) {
                        Text("Kaynak seçip bu ayarı kullan")
                    }
                }
            }
        }
    }
}
