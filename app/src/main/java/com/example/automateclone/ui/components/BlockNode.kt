package com.example.automateclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockCategory
import com.example.automateclone.model.outputPorts

val BLOCK_WIDTH = 180.dp
val BLOCK_HEIGHT = 76.dp
val PORT_SIZE = 16.dp

private val SelectionColor = Color(0xFF1976D2)

private fun categoryColor(category: BlockCategory): Color = when (category) {
    BlockCategory.TRIGGER -> Color(0xFF2E7D32)
    BlockCategory.ACTION -> Color(0xFF1565C0)
    BlockCategory.LOGIC -> Color(0xFFEF6C00)
}

fun outputPortCenterOffset(index: Int, count: Int, blockHeight: Float): Float {
    val center = blockHeight / 2
    if (count <= 1) return center
    val spacing = blockHeight * 0.35f
    return center + spacing * (index - (count - 1) / 2f)
}

@Composable
fun BlockNode(
    block: Block,
    isConnecting: Boolean,
    isSelected: Boolean = false,
    onTapOutputPort: (String) -> Unit,
    onTapInputPort: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = categoryColor(block.type.category)
    val outputPorts = block.type.outputPorts()
    val heightValue = BLOCK_HEIGHT.value

    Box(
        modifier = Modifier
            .width(BLOCK_WIDTH)
            .height(BLOCK_HEIGHT)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) SelectionColor.copy(alpha = 0.18f) else color.copy(alpha = 0.15f)
            ),
            border = BorderStroke(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = when {
                    isConnecting -> Color.Red
                    isSelected -> SelectionColor
                    else -> color
                }
            )
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = block.type.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure", tint = color)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Color.Gray)
                    }
                }
                Text(
                    text = block.config.entries.joinToString { "${it.key}=${it.value}" }
                        .ifBlank { "Tap gear to configure" },
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
        }

        if (block.type.category != BlockCategory.TRIGGER) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-8).dp)
                    .size(PORT_SIZE)
                    .clip(CircleShape)
                    .background(color)
                    .pointerInput(block.id) {
                        detectTapGestures { onTapInputPort() }
                    }
            )
        }

        outputPorts.forEachIndexed { index, portName ->
            val centerOffsetFromTop = outputPortCenterOffset(index, outputPorts.size, heightValue)
            val offsetFromCenter = (centerOffsetFromTop - heightValue / 2).dp

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 8.dp, y = offsetFromCenter)
                    .size(PORT_SIZE)
                    .clip(CircleShape)
                    .background(color)
                    .pointerInput(block.id, portName) {
                        detectTapGestures { onTapOutputPort(portName) }
                    }
            )
            if (outputPorts.size > 1) {
                Text(
                    text = portName.first().uppercaseChar().toString(),
                    fontSize = 8.sp,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 24.dp, y = offsetFromCenter - 6.dp)
                )
            }
        }
    }
}
