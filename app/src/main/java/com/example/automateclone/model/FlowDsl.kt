package com.example.automateclone.model

/**
 * A small text DSL for describing a flow as code — the same graph the
 * visual editor manipulates, just typed instead of dragged.
 *
 * Example:
 * ```
 * flow "Low battery alert" enabled=true {
 *     trigger BATTERY_LEVEL(threshold="20", direction="below") as b1
 *     action SHOW_NOTIFICATION(title="Low battery", text="Charge now") as n1
 *     action VIBRATE(durationMs="500") as v1
 *
 *     b1 -> n1 -> v1
 * }
 * ```
 *
 * Block ids in the DSL (the "as X" alias) are local to the text and are not
 * the same as the underlying Block.id — parsing always creates fresh blocks
 * and auto-arranges them left-to-right by graph depth, since the DSL doesn't
 * encode canvas coordinates.
 */
object FlowDsl {

    class ParseException(message: String) : Exception(message)

    private val blockLineRegex =
        Regex("""^(trigger|action|logic)\s+([A-Z_]+)\s*\(([^)]*)\)\s+as\s+([A-Za-z0-9_]+)$""")
    private val configPairRegex = Regex("""(\w+)\s*=\s*("([^"]*)"|[^,]+)""")

    fun serialize(flow: AutomationFlow): String {
        if (flow.blocks.isEmpty()) {
            return buildString {
                appendLine("flow \"${flow.name}\" enabled=${flow.enabled} {")
                appendLine("    // No blocks yet. Example:")
                appendLine("    // trigger TIME_SCHEDULE(hour=\"7\", minute=\"0\") as t1")
                appendLine("    // action SHOW_NOTIFICATION(title=\"Good morning\", text=\"Rise and shine\") as n1")
                appendLine("    // t1 -> n1")
                appendLine("}")
            }
        }

        val aliasOf = flow.blocks.mapIndexed { i, b -> b.id to "n${i + 1}" }.toMap()

        return buildString {
            appendLine("flow \"${flow.name}\" enabled=${flow.enabled} {")
            flow.blocks.forEach { b ->
                val keyword = b.type.category.name.lowercase()
                val config = b.config.entries.joinToString(", ") { (k, v) -> "$k=\"$v\"" }
                appendLine("    $keyword ${b.type.name}($config) as ${aliasOf[b.id]}")
            }
            if (flow.connections.isNotEmpty()) {
                appendLine()
                flow.connections.forEach { c ->
                    val from = aliasOf[c.fromBlockId]
                    val to = aliasOf[c.toBlockId]
                    if (from != null && to != null) appendLine("    $from -> $to")
                }
            }
            appendLine("}")
        }
    }

    fun parse(text: String, existingId: String? = null): AutomationFlow {
        val headerRegex = Regex("""flow\s+"([^"]*)"\s*(?:enabled\s*=\s*(true|false))?\s*\{""")
        val header = headerRegex.find(text)
            ?: throw ParseException("Expected a header like: flow \"Name\" { ... }")

        val name = header.groupValues[1].ifBlank { "Untitled Flow" }
        val enabled = header.groupValues[2].ifBlank { "true" }.toBoolean()

        val bodyStart = header.range.last + 1
        val bodyEnd = text.lastIndexOf('}')
        if (bodyEnd < bodyStart) throw ParseException("Missing closing '}' for the flow block")
        val body = text.substring(bodyStart, bodyEnd)

        val blocks = mutableListOf<Block>()
        val aliasToId = mutableMapOf<String, String>()
        val connections = mutableListOf<Connection>()

        body.lines().forEachIndexed { idx, rawLine ->
            val line = rawLine.substringBefore("//").trim()
            if (line.isEmpty()) return@forEachIndexed
            val lineNo = idx + 1

            val blockMatch = blockLineRegex.find(line)
            if (blockMatch != null) {
                val (keyword, typeName, configRaw, alias) = blockMatch.destructured
                val type = try {
                    BlockType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    throw ParseException("Line $lineNo: unknown block type '$typeName'")
                }
                if (type.category.name.lowercase() != keyword) {
                    throw ParseException(
                        "Line $lineNo: '$typeName' is a ${type.category.name.lowercase()} block, not '$keyword'"
                    )
                }
                if (aliasToId.containsKey(alias)) {
                    throw ParseException("Line $lineNo: alias '$alias' is already used")
                }
                val config = mutableMapOf<String, String>()
                configPairRegex.findAll(configRaw).forEach { m ->
                    val key = m.groupValues[1]
                    val value = m.groupValues[2].trim().removeSurrounding("\"")
                    config[key] = value
                }
                val block = Block(type = type, config = config)
                blocks += block
                aliasToId[alias] = block.id
                return@forEachIndexed
            }

            if (line.contains("->")) {
                val aliases = line.split("->").map { it.trim() }
                for (i in 0 until aliases.size - 1) {
                    val fromAlias = aliases[i]
                    val toAlias = aliases[i + 1]
                    val fromId = aliasToId[fromAlias]
                        ?: throw ParseException("Line $lineNo: unknown block alias '$fromAlias'")
                    val toId = aliasToId[toAlias]
                        ?: throw ParseException("Line $lineNo: unknown block alias '$toAlias'")
                    connections += Connection(fromBlockId = fromId, toBlockId = toId)
                }
                return@forEachIndexed
            }

            throw ParseException("Line $lineNo: couldn't understand: \"$line\"")
        }

        autoLayout(blocks, connections)

        return AutomationFlow(
            id = existingId ?: java.util.UUID.randomUUID().toString(),
            name = name,
            enabled = enabled,
            blocks = blocks,
            connections = connections
        )
    }

    /** Arranges blocks left-to-right by graph depth since the DSL has no x/y. */
    private fun autoLayout(blocks: List<Block>, connections: List<Connection>) {
        if (blocks.isEmpty()) return
        val layer = mutableMapOf<String, Int>()
        blocks.forEach { layer[it.id] = 0 }
        repeat(blocks.size) {
            connections.forEach { c ->
                val fromLayer = layer[c.fromBlockId] ?: 0
                val toLayer = layer[c.toBlockId] ?: 0
                if (toLayer < fromLayer + 1) layer[c.toBlockId] = fromLayer + 1
            }
        }
        blocks.groupBy { layer[it.id] ?: 0 }
            .toSortedMap()
            .forEach { (layerIdx, blocksInLayer) ->
                blocksInLayer.forEachIndexed { i, b ->
                    b.x = 40f + layerIdx * 260f
                    b.y = 40f + i * 140f
                }
            }
    }
}
