package com.frameinterpolator.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun FPSSelectorCard(
    sourceFps: Double,
    showLowFpsWarning: Boolean,
    selectedFps: Int?,
    availableFps: List<Int>,
    onFpsSelected: (Int) -> Unit,
    onApplyCustomFps: (String) -> Boolean,
    validateCustomFps: (String) -> String?,
    modifier: Modifier = Modifier
) {
    var customFpsText by rememberSaveable(sourceFps, selectedFps, availableFps) {
        mutableStateOf(selectedFps?.takeIf { it !in availableFps }?.toString().orEmpty())
    }
    val customFpsError = if (customFpsText.isBlank()) null else validateCustomFps(customFpsText)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("2. Hedef FPS", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Kaynak: ${sourceFps.roundToInt()} FPS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showLowFpsWarning) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "15 FPS ve altı kaynaklarda ara kareler daha yapay ve düşük kaliteli görünebilir.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (availableFps.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Hazır hedefler tükendi. İstersen aşağıdan özel FPS girebilirsin.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableFps) { fps ->
                        FilterChip(
                            selected = fps == selectedFps,
                            onClick = { onFpsSelected(fps) },
                            label = { Text("$fps FPS") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Özel hedef",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customFpsText,
                        onValueChange = { value ->
                            customFpsText = value.filter(Char::isDigit)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("FPS") },
                        singleLine = true,
                        isError = customFpsError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedButton(
                        onClick = {
                            if (onApplyCustomFps(customFpsText)) {
                                customFpsText = customFpsText.trim()
                            }
                        },
                        enabled = customFpsText.isNotBlank() && customFpsError == null
                    ) {
                        Text("Uygula")
                    }
                }

                if (customFpsError != null) {
                    Text(
                        text = customFpsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            selectedFps?.let {
                Text(
                    text = "Seçili hedef: $it FPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
