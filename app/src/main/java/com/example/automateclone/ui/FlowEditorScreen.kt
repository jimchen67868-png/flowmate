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
import com.example.automateclone.model.BlockCategory
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
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MAX_UNDO_HISTORY = 30

@Composable
fun FlowEditorScreen(initialFlow: AutomationFlow, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val repo = remember { FlowRepository(context) }
    val engine = remember { FlowEngine(context) }

    var flow by remember { mutableStateOf(initialFlow, policy = referentialEqualityPolicy()) }
    var showPalette by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<Block?>(null) }
    var connectingFromId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteBlock by remember { mutableStateOf<Block?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var selectedConnectionId by remember { mutableStateOf<String?>(null) }

    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    var clipboardBlocks by remember { mutableStateOf<List<Block>>(emptyList()) }
    var clipboardConnections by remember { mutableStateOf<List<Connection>>(emptyList()) }

    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var showLog by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<AutomationFlow>() }
    val redoStack = remember { mutableStateListOf<AutomationFlow>() }

    fun persist() = repo.upsert(flow)

    fun snapshotForUndo() {
        undoStack.add(flow.deepCopy())
        if (undoStack.size > MAX_UNDO_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(flow.deepCopy())
        flow = undoStack.removeAt(undoStack.lastIndex)
        persist()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(flow.deepCopy())
        flow = redoStack.removeAt(redoStack.lastIndex)
        persist()
    }

    fun copySelectedToClipboard() {
        clipboardBlocks = flow.blocks.filter { it.id in selectedIds }
            .map { it.copy(config = it.config.toMutableMap()) }
        clipboardConnections = flow.connections.filter { it.fromBlockId in selectedIds && it.toBlockId in selectedIds }
        android.widget.Toast.makeText(
            context, "Copied ${clipboardBlocks.size} block(s)", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun pasteClipboard() {
        if (clipboardBlocks.isEmpty()) return
        snapshotForUndo()
        val idMap = mutableMapOf<String, String>()
        val newBlocks = clipboardBlocks.map { old ->
            val newBlock = Block(type = old.type, x = old.x + 60f, y = old.y + 60f, config = old.config.toMutableMap())
            idMap[old.id] = newBlock.id
            newBlock
        }
        val newConnections = clipboardConnections.mapNotNull { conn ->
            val fromNew = idMap[conn.fromBlockId]
            val toNew = idMap[conn.toBlockId]
            if (fromNew != null && toNew != null) Connection(fromBlockId = fromNew, toBlockId = toNew) else null
        }
        flow.blocks.addAll(newBlocks)
        flow.connections.addAll(newConnections)
        flow = flow.copy(blocks = flow.blocks.toMutableList(), connections = flow.connections.toMutableList())
        selectedIds = newBlocks.map { it.id }.toSet()
        selectMode = true
        persist()
    }

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
                title = { Text(if (selectMode) "${selectedIds.size} selected" else flow.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (selectMode) {
                        if (selectedIds.isNotEmpty()) {
                            TextButton(onClick = { copySelectedToClipboard() }) { Text("Copy") }
                            TextButton(onClick = { pendingBulkDelete = true }) { Text("Delete") }
                        }
                        TextButton(onClick = {
                            selectMode = false
                            selectedIds = emptySet()
                        }) { Text("Done") }
                    } else if (isRunning) {
                        TextButton(onClick = { showLog = !showLog }) {
                            Text(if (showLog) "Editor" else "Log")
                        }
                        TextButton(onClick = {
                            isPaused = !isPaused
                            engine.setPaused(isPaused)
                        }) { Text(if (isPaused) "Resume" else "Pause") }
                        TextButton(onClick = {
                            runningJob?.cancel()
                            runningJob = null
                            isRunning = false
                            isPaused = false
                        }) { Text("Stop") }
                    } else {
                        if (undoStack.isNotEmpty()) {
                            TextButton(onClick = { undo() }) { Text("Undo") }
                        }
                        if (redoStack.isNotEmpty()) {
                            TextButton(onClick = { redo() }) { Text("Redo") }
                        }
                        if (clipboardBlocks.isNotEmpty()) {
                            TextButton(onClick = { pasteClipboard() }) { Text("Paste") }
                        }
                        TextButton(onClick = { selectMode = true }) { Text("Select") }
                        TextButton(onClick = { showCode = !showCode }) {
                            Text(if (showCode) "Visual" else "Code")
                        }
                        TextButton(onClick = { showLog = !showLog }) {
                            Text(if (showLog) "Editor" else "Log")
                        }
                        IconButton(onClick = {
                            val trigger = flow.triggerBlocks().find { it.type == com.example.automateclone.model.BlockType.MANUAL_START }
                                ?: flow.triggerBlocks().firstOrNull()
                            if (trigger != null) {
                                val job = engine.runFrom(flow, trigger)
                                runningJob = job
                                isRunning = true
                                isPaused = false
                                job.invokeOnCompletion {
                                    isRunning = false
                                    isPaused = false
                                    runningJob = null
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context, "Add a trigger block to start this flow", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Run") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showCode && !selectMode && !isRunning && !showLog) {
                FloatingActionButton(onClick = { showPalette = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add block")
                }
            }
        }
    ) { padding ->
        if (showLog) {
            LogScreen(modifier = Modifier.padding(padding))
        } else if (showCode) {
            CodeEditorScreen(
                text = codeText,
                onTextChange = { codeText = it },
                error = codeError,
                onApply = {
                    try {
                        val parsed = FlowDsl.parse(codeText, existingId = flow.id)
                        snapshotForUndo()
                        flow = parsed
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
                    .pointerInput(selectMode) {
                        val blockWidthPx = with(density) { BLOCK_WIDTH.toPx() }
                        val blockHeightPx = with(density) { BLOCK_HEIGHT.toPx() }
                        val portRadiusPx = with(density) { 14.dp.toPx() }
                        val portOffsetPx = with(density) { 8.dp.toPx() }
                        val tapSlopPx = with(density) { 8.dp.toPx() }
                        val connectionTapRadiusPx = with(density) { 20.dp.toPx() }

                        fun distSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
                            val dx = ax - bx
                            val dy = ay - by
                            return dx * dx + dy * dy
                        }

                        fun findNearbyConnection(pos: Offset): Connection? {
                            return flow.connections.find { conn ->
                                val from = flow.blocks.find { it.id == conn.fromBlockId } ?: return@find false
                                val to = flow.blocks.find { it.id == conn.toBlockId } ?: return@find false
                                val p0x = panOffset.x + from.x + blockWidthPx
                                val p0y = panOffset.y + from.y + blockHeightPx / 2
                                val p3x = panOffset.x + to.x
                                val p3y = panOffset.y + to.y + blockHeightPx / 2
                                val midX = (p0x + p3x) / 2
                                val p1x = midX; val p1y = p0y
                                val p2x = midX; val p2y = p3y

                                var minDistSq = Float.MAX_VALUE
                                var t = 0f
                                while (t <= 1f) {
                                    val mt = 1 - t
                                    val x = mt * mt * mt * p0x + 3 * mt * mt * t * p1x + 3 * mt * t * t * p2x + t * t * t * p3x
                                    val y = mt * mt * mt * p0y + 3 * mt * mt * t * p1y + 3 * mt * t * t * p2y + t * t * t * p3y
                                    val d = distSq(x, y, pos.x, pos.y)
                                    if (d < minDistSq) minDistSq = d
                                    t += 0.05f
                                }
                                minDistSq <= connectionTapRadiusPx * connectionTapRadiusPx
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            val hitBlock = flow.blocks.asReversed().find { b ->
                                val left = panOffset.x + b.x - portOffsetPx - portRadiusPx
                                val top = panOffset.y + b.y
                                val right = left + blockWidthPx + 2 * (portOffsetPx + portRadiusPx)
                                val bottom = top + blockHeightPx
                                down.position.x in left..right && down.position.y in top..bottom
                            }

                            when {
                                hitBlock == null -> {
                                    val nearConnection = if (!selectMode) findNearbyConnection(down.position) else null
                                    if (nearConnection != null) {
                                        var accumulated = Offset.Zero
                                        drag(down.id) { change -> accumulated += change.positionChange() }
                                        if (sqrt(accumulated.x * accumulated.x + accumulated.y * accumulated.y) <= tapSlopPx) {
                                            selectedConnectionId = nearConnection.id
                                        }
                                    } else {
                                        drag(down.id) { change ->
                                            panOffset += change.positionChange()
                                            change.consume()
                                        }
                                    }
                                }
                                selectMode -> {
                                    var accumulated = Offset.Zero
                                    var isDragging = false
                                    var snapshotted = false
                                    val idsToMove = if (hitBlock.id in selectedIds) selectedIds else selectedIds + hitBlock.id

                                    drag(down.id) { change ->
                                        val delta = change.positionChange()
                                        accumulated += delta
                                        if (!isDragging && sqrt(accumulated.x * accumulated.x + accumulated.y * accumulated.y) > tapSlopPx) {
                                            isDragging = true
                                            if (!snapshotted) { snapshotForUndo(); snapshotted = true }
                                            flow.blocks.filter { it.id in idsToMove }
                                                .forEach { b -> b.x += accumulated.x; b.y += accumulated.y }
                                            flow = flow.copy(blocks = flow.blocks.toMutableList())
                                        } else if (isDragging) {
                                            flow.blocks.filter { it.id in idsToMove }
                                                .forEach { b -> b.x += delta.x; b.y += delta.y }
                                            flow = flow.copy(blocks = flow.blocks.toMutableList())
                                        }
                                        change.consume()
                                    }

                                    if (isDragging) {
                                        if (hitBlock.id !in selectedIds) selectedIds = selectedIds + hitBlock.id
                                        persist()
                                    } else {
                                        selectedIds = if (hitBlock.id in selectedIds) {
                                            selectedIds - hitBlock.id
                                        } else {
                                            selectedIds + hitBlock.id
                                        }
                                    }
                                }
                                else -> {
                                    val portCy = panOffset.y + hitBlock.y + blockHeightPx / 2
                                    val outputCx = panOffset.x + hitBlock.x + blockWidthPx
                                    val inputCx = panOffset.x + hitBlock.x
                                    val onOutputPort = distSq(down.position.x, down.position.y, outputCx, portCy) <=
                                        portRadiusPx * portRadiusPx
                                    val onInputPort = hitBlock.type.category != BlockCategory.TRIGGER &&
                                        distSq(down.position.x, down.position.y, inputCx, portCy) <= portRadiusPx * portRadiusPx

                                    if (onOutputPort || onInputPort) {
                                        // Leave it to the port's own tap detector.
                                    } else {
                                        var snapshotted = false
                                        drag(down.id) { change ->
                                            if (!snapshotted) { snapshotForUndo(); snapshotted = true }
                                            val delta = change.positionChange()
                                            hitBlock.x += delta.x
                                            hitBlock.y += delta.y
                                            flow = flow.copy(blocks = flow.blocks.toMutableList())
                                            change.consume()
                                        }
                                    }
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
                    ConnectionsCanvas(flow = flow, density = density, selectedConnectionId = selectedConnectionId)

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
                                isSelected = block.id in selectedIds,
                                onTapOutputPort = {
                                    if (!selectMode) connectingFromId = block.id
                                },
                                onTapInputPort = {
                                    if (!selectMode) {
                                        val fromId = connectingFromId
                                        if (fromId != null && fromId != block.id) {
                                            snapshotForUndo()
                                            flow.connections.add(Connection(fromBlockId = fromId, toBlockId = block.id))
                                            flow = flow.copy(connections = flow.connections.toMutableList())
                                            connectingFromId = null
                                            persist()
                                        }
                                    }
                                },
                                onEdit = { if (!selectMode) editingBlock = block },
                                onDelete = { pendingDeleteBlock = block }
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
                val index = flow.blocks.size
                val colGapPx = with(density) { (BLOCK_WIDTH + 60.dp).toPx() }
                val rowGapPx = with(density) { (BLOCK_HEIGHT + 60.dp).toPx() }
                val spawnX = 40f + (index % 4) * colGapPx
                val spawnY = 40f + (index / 4) * rowGapPx
                snapshotForUndo()
                flow.blocks.add(Block(type = type, x = spawnX, y = spawnY))
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
                snapshotForUndo()
                block.config.clear()
                block.config.putAll(newConfig)
                flow = flow.copy(blocks = flow.blocks.toMutableList())
                editingBlock = null
                persist()
            },
            onDismiss = { editingBlock = null }
        )
    }

    pendingDeleteBlock?.let { block ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBlock = null },
            title = { Text("Delete block?") },
            text = { Text("Delete \"${block.type.displayName}\" and any connections to it? This can be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    snapshotForUndo()
                    flow.blocks.remove(block)
                    flow.connections.removeAll { it.fromBlockId == block.id || it.toBlockId == block.id }
                    flow = flow.copy(
                        blocks = flow.blocks.toMutableList(),
                        connections = flow.connections.toMutableList()
                    )
                    persist()
                    pendingDeleteBlock = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBlock = null }) { Text("Cancel") }
            }
        )
    }

    if (pendingBulkDelete) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("Delete ${selectedIds.size} blocks?") },
            text = { Text("This removes the selected blocks and any connections to them. This can be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    snapshotForUndo()
                    flow.blocks.removeAll { it.id in selectedIds }
                    flow.connections.removeAll { it.fromBlockId in selectedIds || it.toBlockId in selectedIds }
                    flow = flow.copy(
                        blocks = flow.blocks.toMutableList(),
                        connections = flow.connections.toMutableList()
                    )
                    selectedIds = emptySet()
                    persist()
                    pendingBulkDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) { Text("Cancel") }
            }
        )
    }

    selectedConnectionId?.let { connId ->
        AlertDialog(
            onDismissRequest = { selectedConnectionId = null },
            title = { Text("Delete this connection?") },
            text = { Text("This removes the link between these two blocks. This can be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    snapshotForUndo()
                    flow.connections.removeAll { it.id == connId }
                    flow = flow.copy(connections = flow.connections.toMutableList())
                    persist()
                    selectedConnectionId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { selectedConnectionId = null }) { Text("Cancel") }
            }
        )
    }
}
