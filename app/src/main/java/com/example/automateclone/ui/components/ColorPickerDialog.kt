package com.example.automateclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun parseColorSafely(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    null
}

private val PRESET_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
    "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E",
    "#607D8B", "#000000", "#FFFFFF"
)

@Composable
fun ColorPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var customHex by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Choose a color", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            PRESET_COLORS.chunked(6).forEach { rowColors ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseColorSafely(hex) ?: Color.Gray)
                                .clickable { onPick(hex) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it },
                    label = { Text("Custom hex, e.g. #FF5722") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    if (customHex.isNotBlank()) {
                        onPick(if (customHex.startsWith("#")) customHex else "#$customHex")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
            ) { Text("Use custom color") }
        }
    }
}
