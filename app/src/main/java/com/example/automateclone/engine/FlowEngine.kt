package com.example.automateclone.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.automateclone.actions.ActionExecutor
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockCategory
import com.example.automateclone.model.BlockType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FlowEngine(private val context: Context) {

    private val pausedFlag = AtomicBoolean(false)

    fun setPaused(paused: Boolean) {
        pausedFlag.set(paused)
    }

    fun runFrom(flow: AutomationFlow, triggerBlock: Block, scope: CoroutineScope = CoroutineScope(Dispatchers.Main)): Job {
        if (!flow.enabled) {
            FlowLog.add(flow.name, "Flow is disabled — nothing to run", LogLevel.ERROR)
            return Job().apply { complete() }
        }
        pausedFlag.set(false)
        FlowLog.add(flow.name, "Run started (trigger: ${triggerBlock.type.displayName})")
        return scope.launch {
            try {
                val visited = mutableSetOf<String>()
                val variables = mutableMapOf<String, String>()
                walk(flow, triggerBlock, visited, variables)
                FlowLog.add(flow.name, "Run finished")
            } catch (e: CancellationException) {
                FlowLog.add(flow.name, "Run stopped")
                throw e
            }
        }
    }

    private suspend fun awaitIfPaused() {
        while (pausedFlag.get()) {
            delay(150)
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

        yield()
        awaitIfPaused()

        when (block.type.category) {
            BlockCategory.ACTION -> {
                FlowLog.add(flow.name, "Running: ${block.type.displayName}")
                try {
                    val resolved = block.copy(
                        config = block.config.mapValues { substituteVariables(it.value, variables) }.toMutableMap()
                    )
                    ActionExecutor.execute(context, resolved)
                } catch (t: Throwable) {
                    FlowLog.add(flow.name, "Failed: ${block.type.displayName} — ${t.message}", LogLevel.ERROR)
                    Log.e("FlowEngine", "Action ${block.type} failed", t)
                }
            }
            BlockCategory.LOGIC -> {
                when (block.type) {
                    BlockType.WAIT -> {
                        val ms = block.config["durationMs"]?.toLongOrNull() ?: 0L
                        FlowLog.add(flow.name, "Waiting ${ms}ms")
                        delay(ms)
                    }
                    BlockType.SET_VARIABLE -> {
                        val name = block.config["name"].orEmpty()
                        if (name.isNotBlank()) {
                            val value = substituteVariables(block.config["value"].orEmpty(), variables)
                            variables[name] = value
                            FlowLog.add(flow.name, "Set variable $name = $value")
                        }
                    }
                    BlockType.IF_CONDITION -> {
                        val passed = evaluateCondition(block, variables)
                        FlowLog.add(
                            flow.name,
                            "If ${block.config["variable"]} ${block.config["operator"]} ${block.config["value"]}: " +
                                if (passed) "true" else "false (stopping this branch)"
                        )
                        if (!passed) return
                    }
                    BlockType.LOOP -> {
                        val count = (block.config["count"]?.toIntOrNull() ?: 1).coerceAtLeast(0)
                        FlowLog.add(flow.name, "Loop x$count")
                        repeat(count) { i ->
                            FlowLog.add(flow.name, "Loop iteration ${i + 1}/$count")
                            for (next in flow.outgoingFrom(block.id)) {
                                walk(flow, next, mutableSetOf(), variables)
                            }
                        }
                        return
                    }
                    BlockType.SHELL_COMMAND -> {
                        val command = substituteVariables(block.config["command"].orEmpty(), variables)
                        val outputVar = block.config["outputVariable"].orEmpty()
                        val result = runShellCommand(command)
                        FlowLog.add(flow.name, "Shell: $command -> ${result.take(120)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.OCR_IMAGE -> {
                        val path = substituteVariables(block.config["imagePath"].orEmpty(), variables)
                        val outputVar = block.config["outputVariable"].orEmpty()
                        val result = runOcr(path)
                        FlowLog.add(flow.name, "OCR($path) -> ${result.take(80)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
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

    private suspend fun runShellCommand(command: String): String = withContext(Dispatchers.IO) {
        if (command.isBlank()) return@withContext "Error: no command given"
        try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                "Error: command timed out after 10s"
            } else {
                output.trim()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun runOcr(path: String): String = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext "Error: no image path given"
        try {
            val uri = when {
                path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
                else -> Uri.fromFile(File(path))
            }
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).await().text
        } catch (e: Exception) {
            "Error: ${e.message}"
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
