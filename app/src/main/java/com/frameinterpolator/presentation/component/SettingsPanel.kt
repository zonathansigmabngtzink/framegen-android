package com.frameinterpolator.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameinterpolator.data.model.OutputSize
import com.frameinterpolator.data.model.ProcessingConfig

@Composable
fun QualityCard(
    selectedQuality: ProcessingConfig.QualityPreset,
    onQualityChanged: (ProcessingConfig.QualityPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("4. Kalite", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProcessingConfig.QualityPreset.entries.toList()) { quality ->
                    FilterChip(
                        selected = quality == selectedQuality,
                        onClick = { onQualityChanged(quality) },
                        label = { Text(quality.label) },
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
                    text = selectedQuality.description,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ResolutionCard(
    sourceWidth: Int,
    sourceHeight: Int,
    selectedSize: OutputSize?,
    availableSizes: List<OutputSize>,
    onResolutionChanged: (OutputSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("3. Çıktı boyutu", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Kaynak: ${sourceWidth}x${sourceHeight}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableSizes) { size ->
                    FilterChip(
                        selected = selectedSize?.width == size.width && selectedSize.height == size.height,
                        onClick = { onResolutionChanged(size) },
                        label = { Text(size.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            selectedSize?.let {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Seçili çıktı: ${it.width}x${it.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
