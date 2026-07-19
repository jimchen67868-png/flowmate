package com.example.automateclone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.automateclone.model.BlockCategory
import com.example.automateclone.model.BlockType

@Composable
fun BlockPaletteSheet(onPick: (BlockType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            BlockCategory.entries.forEach { category ->
                item {
                    Text(
                        text = category.name.lowercase().replaceFirstChar { it.uppercase() } + "s",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(BlockType.entries.filter { it.category == category }) { type ->
                    ListItem(
                        headlineContent = { Text(type.displayName) },
                        modifier = Modifier.clickable { onPick(type) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
