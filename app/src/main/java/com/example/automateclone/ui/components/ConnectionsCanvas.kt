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
import com.example.automateclone.model.outputPorts

@Composable
fun ConnectionsCanvas(flow: AutomationFlow, density: Density, selectedConnectionId: String? = null) {
    val blockWidthPx = with(density) { BLOCK_WIDTH.toPx() }
    val blockHeightPx = with(density) { BLOCK_HEIGHT.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        flow.connections.forEach { conn ->
            val from = flow.blocks.find { it.id == conn.fromBlockId } ?: return@forEach
            val to = flow.blocks.find { it.id == conn.toBlockId } ?: return@forEach

            val fromPorts = from.type.outputPorts()
            val fromPortName = conn.fromPort ?: "output"
            val fromIndex = fromPorts.indexOf(fromPortName).let { if (it == -1) 0 else it }
            val startY = from.y + outputPortCenterOffset(fromIndex, fromPorts.size, blockHeightPx)

            val start = Offset(from.x + blockWidthPx, startY)
            val end = Offset(to.x, to.y + blockHeightPx / 2)
            val midX = (start.x + end.x) / 2

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(midX, start.y, midX, end.y, end.x, end.y)
            }
            val isSelected = conn.id == selectedConnectionId
            drawPath(
                path,
                color = if (isSelected) Color(0xFFD32F2F) else Color(0xFF9575CD),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isSelected) 7f else 4f)
            )
        }
    }
}
