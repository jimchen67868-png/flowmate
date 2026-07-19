package com.example.automateclone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.automateclone.model.AutomationFlow

/**
 * Draws a curved line from each block's output port to its connected block's
 * input port. Positioned as a full-size background layer under the block cards.
 */
@Composable
fun ConnectionsCanvas(flow: AutomationFlow, density: Density) {
    val blockWidthPx = with(density) { BLOCK_WIDTH.toPx() }
    val blockHeightPx = with(density) { BLOCK_HEIGHT.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        flow.connections.forEach { conn ->
            val from = flow.blocks.find { it.id == conn.fromBlockId } ?: return@forEach
            val to = flow.blocks.find { it.id == conn.toBlockId } ?: return@forEach

            val start = Offset(from.x + blockWidthPx, from.y + blockHeightPx / 2)
            val end = Offset(to.x, to.y + blockHeightPx / 2)
            val midX = (start.x + end.x) / 2

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(midX, start.y, midX, end.y, end.x, end.y)
            }
            drawPath(path, color = Color(0xFF9575CD), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
    }
}
