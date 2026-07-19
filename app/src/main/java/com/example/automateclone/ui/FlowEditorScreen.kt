package com.example.automateclone.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.model.Block
import com.example.automateclone.model.Connection
import com.example.automateclone.model.FlowRepository
import com.example.automateclone.ui.components.BlockConfigDialog
import com.example.automateclone.ui.components.BlockNode
import com.example.automateclone.ui.components.BlockPaletteSheet
import com.example.automateclone.ui.components.ConnectionsCanvas
import kotlin.math.roundToInt

/**
 * The visual flow builder: a scrollable canvas where blocks are dragged
 * around, connected by tapping ports, and configured via a dialog. Mirrors
 * the core interaction model of Automate's flow editor, simplified.
 */
@Composable
fun FlowEditorScreen(initialFlow: AutomationFlow, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val repo = remember { FlowRepository(context) }
    val engine = remember { FlowEngine(context) }

    var flow by remember { mutableStateOf(initialFlow) }
    var showPalette by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<Block?>(null) }
    // Block id currently "armed" for connecting — tap an input port on another block to finish.
    var connectingFromId by remember { mutableStateOf<String?>(null) }

    fun persist() = repo.upsert(flow)

    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(flow.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = {
                        val trigger = flow.triggerBlocks().firstOrNull()
                        if (trigger != null) engine.runFrom(flow, trigger)
                    }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Test run") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPalette = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add block")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset += dragAmount
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(panOffset.x.roundToInt(), panOffset.y.roundToInt()) }
                    .size(2000.dp)
            ) {
                ConnectionsCanvas(flow = flow, density = density)

                flow.blocks.forEach { block ->
                    Box(
                        modifier = Modifier.offset(
                            x = with(density) { block.x.toDp() },
                            y = with(density) { block.y.toDp() }
                        )
                    ) {
                        BlockNode(
                            block = block,
                            isConnecting = connectingFromId == block.id,
                            onDrag = { delta ->
                                block.x += delta.x
                                block.y += delta.y
                                flow = flow.copy(blocks = flow.blocks.toMutableList())
                            },
                            onTapOutputPort = {
                                connectingFromId = block.id
                            },
                            onTapInputPort = {
                                val fromId = connectingFromId
                                if (fromId != null && fromId != block.id) {
                                    flow.connections.add(Connection(fromBlockId = fromId, toBlockId = block.id))
                                    flow = flow.copy(connections = flow.connections.toMutableList())
                                    connectingFromId = null
                                    persist()
                                }
                            },
                            onEdit = { editingBlock = block },
                            onDelete = {
                                flow.blocks.remove(block)
                                flow.connections.removeAll { it.fromBlockId == block.id || it.toBlockId == block.id }
                                flow = flow.copy(
                                    blocks = flow.blocks.toMutableList(),
                                    connections = flow.connections.toMutableList()
                                )
                                persist()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPalette) {
        BlockPaletteSheet(
            onPick = { type ->
                flow.blocks.add(Block(type = type, x = 40f, y = 40f))
                flow = flow.copy(blocks = flow.blocks.toMutableList())
                showPalette = false
                persist()
            },
            onDismiss = { showPalette = false }
        )
    }

    editingBlock?.let { block ->
        BlockConfigDialog(
            block = block,
            onSave = { newConfig ->
                block.config.clear()
                block.config.putAll(newConfig)
                flow = flow.copy(blocks = flow.blocks.toMutableList())
                editingBlock = null
                persist()
            },
            onDismiss = { editingBlock = null }
        )
    }
}
