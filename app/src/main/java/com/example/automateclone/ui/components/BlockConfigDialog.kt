package com.example.automateclone.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockType

private fun currentPartialVariable(value: TextFieldValue): String? {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return null
    val segment = before.substring(lastOpen + 2)
    if (segment.contains("}") || segment.contains("$") || segment.contains(" ")) return null
    return segment
}

private fun insertVariableAtCursor(value: TextFieldValue, varName: String): TextFieldValue {
    val cursor = value.selection.start
    val insertion = "\${$varName}"
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    return TextFieldValue(newText, TextRange(cursor + insertion.length))
}

private fun completeVariable(value: TextFieldValue, fullName: String): TextFieldValue {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return value
    val prefix = value.text.substring(0, lastOpen + 2)
    val suffix = value.text.substring(cursor)
    val newText = prefix + fullName + "}" + suffix
    return TextFieldValue(newText, TextRange(prefix.length + fullName.length + 1))
}

@Composable
fun BlockConfigDialog(
    block: Block,
    availableVariables: List<String> = emptyList(),
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fields = remember {
        mutableStateMapOf<String, TextFieldValue>().apply {
            block.config.forEach { (k, v) -> put(k, TextFieldValue(v)) }
        }
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var colorPickerKey by remember { mutableStateOf<String?>(null) }
    var fxMenuKey by remember { mutableStateOf<String?>(null) }

    if (showAppPicker) {
        AppPickerDialog(
            onPick = { packageName, _ ->
                fields["packageName"] = TextFieldValue(packageName)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
        return
    }

    colorPickerKey?.let { key ->
        ColorPickerDialog(
            onPick = { hex ->
                fields[key] = TextFieldValue(hex)
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
                            val currentPackage = fields[key]?.text.orEmpty()
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
                            val current = fields[key]?.text.orEmpty()
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
                            val value = fields[key] ?: TextFieldValue("")
                            val partial = currentPartialVariable(value)
                            val suggestions = if (partial != null) {
                                availableVariables.filter { it.startsWith(partial, ignoreCase = true) }
                            } else emptyList()

                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { fields[key] = it },
                                        label = { Text(key) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (availableVariables.isNotEmpty()) {
                                        Box {
                                            TextButton(onClick = { fxMenuKey = key }) { Text("fx") }
                                            DropdownMenu(
                                                expanded = fxMenuKey == key,
                                                onDismissRequest = { fxMenuKey = null }
                                            ) {
                                                availableVariables.forEach { name ->
                                                    DropdownMenuItem(
                                                        text = { Text(name) },
                                                        onClick = {
                                                            fields[key] = insertVariableAtCursor(value, name)
                                                            fxMenuKey = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (suggestions.isNotEmpty()) {
                                    Row(Modifier.padding(top = 2.dp)) {
                                        suggestions.forEach { name ->
                                            TextButton(
                                                onClick = { fields[key] = completeVariable(value, name) },
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Text(name, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fields.mapValues { it.value.text }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
