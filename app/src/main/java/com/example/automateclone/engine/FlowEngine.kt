package com.example.automateclone.engine

import android.content.Context
import android.util.Log
import com.example.automateclone.actions.ActionExecutor
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockCategory
import com.example.automateclone.model.BlockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlowEngine(private val context: Context) {

    fun runFrom(flow: AutomationFlow, triggerBlock: Block, scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        if (!flow.enabled) return
        scope.launch {
            val visited = mutableSetOf<String>()
            val variables = mutableMapOf<String, String>()
            walk(flow, triggerBlock, visited, variables)
        }
    }

    private suspend fun walk(
        flow: AutomationFlow,
        block: Block,
        visited: MutableSet<String>,
        variables: MutableMap<String, String>
    ) {
        if (block.id in visited) return
        visited += block.id

        when (block.type.category) {
            BlockCategory.ACTION -> {
                try {
                    val resolved = block.copy(
                        config = block.config.mapValues { substituteVariables(it.value, variables) }.toMutableMap()
                    )
                    ActionExecutor.execute(context, resolved)
                } catch (t: Throwable) {
                    Log.e("FlowEngine", "Action ${block.type} failed", t)
                }
            }
            BlockCategory.LOGIC -> {
                when (block.type) {
                    BlockType.WAIT -> {
                        val ms = block.config["durationMs"]?.toLongOrNull() ?: 0L
                        delay(ms)
                    }
                    BlockType.SET_VARIABLE -> {
                        val name = block.config["name"].orEmpty()
                        if (name.isNotBlank()) {
                            variables[name] = substituteVariables(block.config["value"].orEmpty(), variables)
                        }
                    }
                    BlockType.IF_CONDITION -> {
                        if (!evaluateCondition(block, variables)) return
                    }
                    BlockType.LOOP -> {
                        val count = (block.config["count"]?.toIntOrNull() ?: 1).coerceAtLeast(0)
                        repeat(count) {
                            for (next in flow.outgoingFrom(block.id)) {
                                walk(flow, next, mutableSetOf(), variables)
                            }
                        }
                        return
                    }
                    else -> {}
                }
            }
            BlockCategory.TRIGGER -> { }
        }

        for (next in flow.outgoingFrom(block.id)) {
            walk(flow, next, visited, variables)
        }
    }

    private fun evaluateCondition(block: Block, variables: Map<String, String>): Boolean {
        val actual = variables[block.config["variable"].orEmpty()].orEmpty()
        val expected = block.config["value"].orEmpty()
        val actualNum = actual.toDoubleOrNull()
        val expectedNum = expected.toDoubleOrNull()

        return when (block.config["operator"]) {
            "equals" -> actual == expected
            "notEquals" -> actual != expected
            "contains" -> actual.contains(expected)
            "greaterThan" -> if (actualNum != null && expectedNum != null) actualNum > expectedNum else actual > expected
            "lessThan" -> if (actualNum != null && expectedNum != null) actualNum < expectedNum else actual < expected
            else -> false
        }
    }

    private fun substituteVariables(text: String, variables: Map<String, String>): String {
        val regex = Regex("""\$\{(\w+)\}""")
        return regex.replace(text) { match -> variables[match.groupValues[1]] ?: match.value }
    }
}
