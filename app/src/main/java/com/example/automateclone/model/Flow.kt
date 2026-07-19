package com.example.automateclone.model

import java.util.UUID

/**
 * A single node on the canvas: a trigger, action, or logic block.
 * `x`/`y` are canvas coordinates (top-left of the block), used for dragging.
 * `config` holds the user-entered values for BlockType.configKeys.
 */
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    var x: Float = 0f,
    var y: Float = 0f,
    val config: MutableMap<String, String> = mutableMapOf()
)

/**
 * A directed edge from one block's output to another block's input.
 * Flows execute by following connections starting at trigger blocks.
 */
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val fromBlockId: String,
    val toBlockId: String
)

/**
 * A complete automation graph: the blocks placed on the canvas and the
 * connections between them. Saved/loaded as JSON (see FlowRepository).
 */
data class AutomationFlow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Flow",
    var enabled: Boolean = true,
    val blocks: MutableList<Block> = mutableListOf(),
    val connections: MutableList<Connection> = mutableListOf()
) {
    fun outgoingFrom(blockId: String): List<Block> =
        connections.filter { it.fromBlockId == blockId }
            .mapNotNull { conn -> blocks.find { it.id == conn.toBlockId } }

    fun triggerBlocks(): List<Block> = blocks.filter { it.type.category == BlockCategory.TRIGGER }
}
