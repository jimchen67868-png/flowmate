package com.example.automateclone.model

import java.util.UUID

data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    var x: Float = 0f,
    var y: Float = 0f,
    val config: MutableMap<String, String> = mutableMapOf()
)

data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val fromBlockId: String,
    val toBlockId: String,
    val fromPort: String? = null
)

data class AutomationFlow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Flow",
    var enabled: Boolean = true,
    val blocks: MutableList<Block> = mutableListOf(),
    val connections: MutableList<Connection> = mutableListOf()
) {
    fun outgoingFrom(blockId: String, port: String = "output"): List<Block> =
        connections.filter { it.fromBlockId == blockId && (it.fromPort ?: "output") == port }
            .mapNotNull { conn -> blocks.find { it.id == conn.toBlockId } }

    fun triggerBlocks(): List<Block> = blocks.filter { it.type.category == BlockCategory.TRIGGER }

    fun deepCopy(): AutomationFlow = copy(
        blocks = blocks.map { it.copy(config = it.config.toMutableMap()) }.toMutableList(),
        connections = connections.map { it.copy() }.toMutableList()
    )
}
