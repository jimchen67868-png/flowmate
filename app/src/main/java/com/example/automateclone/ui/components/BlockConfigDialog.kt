package com.example.automateclone.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.example.automateclone.model.Block

@Composable
fun BlockConfigDialog(block: Block, onSave: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val fields = remember { mutableStateMapOf<String, String>().apply { putAll(block.config) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${block.type.displayName}") },
        text = {
            Column {
                block.type.configKeys.forEach { key ->
                    OutlinedTextField(
                        value = fields[key] ?: "",
                        onValueChange = { fields[key] = it },
                        label = { Text(key) },
                        modifier = androidx.compose.ui.Modifier.padding(vertical = 4.dp)
                    )
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
