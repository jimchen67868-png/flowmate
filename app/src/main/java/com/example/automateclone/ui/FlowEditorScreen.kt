package com.example.automateclone.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.model.Block
import com.example.automateclone.model.Connection
import com.example.automateclone.model.FlowDsl
import com.example.automateclone.model.FlowRepository
import com.example.automateclone.ui.components.BLOCK_HEIGHT
import com.example.automateclone.ui.components.BLOCK_WIDTH
import com.example.automateclone.ui.components.BlockConfigDialog
import com.example.automateclone.ui.components.BlockNode
import com.example.automateclone.ui.components.BlockPaletteSheet
import com.example.automateclone.ui.components.CodeEditorScreen
import com.example.automateclone.ui.components.ConnectionsCanvas
import kotlin.math.roundToInt

@Composable
fun FlowEditorScreen(initialFlow: AutomationFlow, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val repo = remember { FlowRepository(context) }
    val engine = remember { FlowEngine(context) }

    var flow by remember { mutableStateOf(initialFlow) }
    var showPalette by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<Block?>(null) }
    var connectingFromId by remember { mutableStateOf<String?>(null) }

    fun persist() = repo.upsert(flow)

    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showCode by remember { mutableStateOf(false) }
    var codeText by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showCode) {
        if (showCode) {
            codeText = FlowDsl.serialize(flow)
            codeError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(flow.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { showCode = !showCode }) {
                        Text(if (showCode) "Visual" else "Code")
                    }
                    IconButton(onClick = {
                        val trigger = flow.triggerBlocks().firstOrNull()
                        if (trigger != null) engine.runFrom(flow, trigger)
                    }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Test run") }
                }
            )
        },
        floatingActionButton = {
            if (!showCode) {
                FloatingActionButton(onClick = { showPalette = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add block")
                }
            }
        }
    ) { padding ->
        if (showCode) {
            CodeEditorScreen(
                text = codeText,
                onTextChange = { codeText = it },
                error = codeError,
                onApply = {
                    try {
                        flow = FlowDsl.parse(codeText, existingId = flow.id)
                        persist()
                        codeError = null
                        showCode = false
                    } catch (e: Exception) {
                        codeError = e.message ?: "Couldn't parse this flow"
                    }
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        val blockWidthPx = with(density) { BLOCK_WIDTH.toPx() }
                        val blockHeightPx = with(density) { BLOCK_HEIGHT.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val touchedABlock = flow.blocks.any { b ->
                                val left = panOffset.x + b.x
                                val top = panOffset.y + b.y
                                down.position.x in left..(left + blockWidthPx) &&
                                    down.position.y in top..(top + blockHeightPx)
                            }
                            if (!touchedABlock) {
                                drag(down.id) { change ->
                                    panOffset += change.positionChange()
                                    change.consume()
                                }
                            }
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
