package com.example.automateclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockType

@Composable
fun BlockConfigDialog(block: Block, onSave: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fields = remember { mutableStateMapOf<String, String>().apply { putAll(block.config) } }
    var showAppPicker by remember { mutableStateOf(false) }
    var colorPickerKey by remember { mutableStateOf<String?>(null) }

    if (showAppPicker) {
        AppPickerDialog(
            onPick = { packageName, _ ->
                fields["packageName"] = packageName
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
        return
    }

    colorPickerKey?.let { key ->
        ColorPickerDialog(
            onPick = { hex ->
                fields[key] = hex
                colorPickerKey = null
            },
            onDismiss = { colorPickerKey = null }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${block.type.displayName}") },
        text = {
            Column {
                block.type.configKeys.forEach { key ->
                    when {
                        block.type == BlockType.LAUNCH_APP && key == "packageName" -> {
                            val currentPackage = fields[key].orEmpty()
                            val label = remember(currentPackage) {
                                if (currentPackage.isBlank()) {
                                    null
                                } else {
                                    try {
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(currentPackage, 0)
                                        ).toString()
                                    } catch (e: Exception) {
                                        currentPackage
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { showAppPicker = true },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(label ?: "Choose an app")
                            }
                        }
                        key == "colorHex" || key == "color" -> {
                            val current = fields[key].orEmpty()
                            OutlinedButton(
                                onClick = { colorPickerKey = key },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(parseColorSafely(current) ?: Color.Gray)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (current.isBlank()) "Choose a color (optional)" else current)
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = fields[key] ?: "",
                                onValueChange = { fields[key] = it },
                                label = { Text(key) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fields.toMap()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
