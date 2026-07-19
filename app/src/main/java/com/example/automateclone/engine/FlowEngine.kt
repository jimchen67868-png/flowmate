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

/**
 * Executes an AutomationFlow starting from a specific trigger block that just
 * fired. Walks the connection graph depth-first, running ACTION blocks and
 * evaluating LOGIC blocks (wait/if) along the way.
 *
 * This is intentionally a simple sequential walker — good enough for the
 * linear/branching flows the visual editor produces. Loops in the graph are
 * not supported yet (guarded against with a visited-set to avoid infinite runs).
 */
class FlowEngine(private val context: Context) {

    fun runFrom(flow: AutomationFlow, triggerBlock: Block, scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        if (!flow.enabled) return
        scope.launch {
            val visited = mutableSetOf<String>()
            walk(flow, triggerBlock, visited)
        }
    }

    private suspend fun walk(flow: AutomationFlow, block: Block, visited: MutableSet<String>) {
        if (block.id in visited) return
        visited += block.id

        when (block.type.category) {
            BlockCategory.ACTION -> {
                try {
                    ActionExecutor.execute(context, block)
                } catch (t: Throwable) {
                    Log.e("FlowEngine", "Action ${block.type} failed", t)
                }
            }
            BlockCategory.LOGIC -> handleLogic(block)
            BlockCategory.TRIGGER -> { /* only the entry block is a trigger; ignore mid-graph */ }
        }

        for (next in flow.outgoingFrom(block.id)) {
            walk(flow, next, visited)
        }
    }

    private suspend fun handleLogic(block: Block) {
        when (block.type) {
            BlockType.WAIT -> {
                val ms = block.config["durationMs"]?.toLongOrNull() ?: 0L
                delay(ms)
            }
            BlockType.IF_CONDITION -> {
                // Placeholder: real condition evaluation would read a shared
                // variable store. For now this always passes through, since
                // wiring an IF's "false" branch is a future editor feature.
            }
            else -> {}
        }
    }
}
