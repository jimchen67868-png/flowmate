package com.example.automateclone.engine

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.automateclone.actions.ActionExecutor
import com.example.automateclone.actions.GestureAccessibilityService
import com.example.automateclone.actions.ScreenCaptureService
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

private sealed class FindImageOutcome {
    data class Found(val x: Int, val y: Int, val score: Double) : FindImageOutcome()
    object NotFound : FindImageOutcome()
    data class Error(val message: String) : FindImageOutcome()
}

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

    /**
     * Parses a duration like "500" (bare number = milliseconds, for backward
     * compatibility), "30s", "5m", "2h", "1d" (case-insensitive), or decimals
     * like "1.5h". Returns null if the string doesn't match.
     */
    private fun parseDurationMs(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val match = Regex("""^(\d+(?:\.\d+)?)\s*([smhd]?)$""", RegexOption.IGNORE_CASE).find(trimmed) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "s" -> 1_000.0
            "m" -> 60_000.0
            "h" -> 3_600_000.0
            "d" -> 86_400_000.0
            else -> 1.0 // no unit = milliseconds
        }
        return (amount * multiplier).toLong()
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
                        val raw = block.config["duration"]?.takeIf { it.isNotBlank() }
                            ?: block.config["durationMs"].orEmpty() // legacy key from older saved flows
                        val ms = parseDurationMs(raw) ?: 0L
                        FlowLog.add(flow.name, "Waiting ${raw.ifBlank { "0" }} (${ms}ms)")
                        delay(ms)
                    }
                    BlockType.SET_VARIABLE -> {
                        val name = normalizeVariableName(block.config["name"].orEmpty())
                        if (name.isNotBlank()) {
                            val value = substituteVariables(block.config["value"].orEmpty(), variables)
                            variables[name] = value
                            FlowLog.add(flow.name, "Set variable $name = $value")
                        }
                    }
                    BlockType.IF_CONDITION -> {
                        val passed = evaluateCondition(block, variables)
                        val branch = if (passed) "true" else "false"
                        FlowLog.add(
                            flow.name,
                            "If ${block.config["variable"]} ${block.config["operator"]} ${block.config["value"]}: " +
                                "$branch — following the '$branch' branch"
                        )
                        for (next in flow.outgoingFrom(block.id, branch)) {
                            walk(flow, next, visited, variables)
                        }
                        return
                    }
                    BlockType.LOOP -> {
                        val count = (block.config["count"]?.toIntOrNull() ?: 1).coerceAtLeast(0)
                        FlowLog.add(flow.name, "Loop x$count")
                        repeat(count) { i ->
                            FlowLog.add(flow.name, "Loop iteration ${i + 1}/$count")
                            for (next in flow.outgoingFrom(block.id, "body")) {
                                walk(flow, next, mutableSetOf(), variables)
                            }
                        }
                        FlowLog.add(flow.name, "Loop finished, continuing after it")
                        for (next in flow.outgoingFrom(block.id, "after")) {
                            walk(flow, next, visited, variables)
                        }
                        return
                    }
                    BlockType.SHELL_COMMAND -> {
                        val command = substituteVariables(block.config["command"].orEmpty(), variables)
                        val outputVar = normalizeVariableName(block.config["outputVariable"].orEmpty())
                        FlowLog.add(flow.name, "Shell: running $command (10s timeout)")
                        val result = runShellCommand(command)
                        FlowLog.add(flow.name, "Shell: $command -> ${result.take(120)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.OCR_IMAGE -> {
                        val path = substituteVariables(block.config["imagePath"].orEmpty(), variables)
                        val outputVar = normalizeVariableName(block.config["outputVariable"].orEmpty())
                        val result = runOcr(path)
                        FlowLog.add(flow.name, "OCR($path) -> ${result.take(80)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.SCREENSHOT -> {
                        val outputVar = normalizeVariableName(block.config["outputVariable"].orEmpty())
                        val result = captureScreenshotPreferred()
                        FlowLog.add(flow.name, "Screenshot -> ${result.take(80)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.CROP_IMAGE -> {
                        val path = substituteVariables(block.config["imagePath"].orEmpty(), variables)
                        val x = substituteVariables(block.config["x"].orEmpty(), variables).toIntOrNull() ?: 0
                        val y = substituteVariables(block.config["y"].orEmpty(), variables).toIntOrNull() ?: 0
                        val w = substituteVariables(block.config["width"].orEmpty(), variables).toIntOrNull() ?: 0
                        val h = substituteVariables(block.config["height"].orEmpty(), variables).toIntOrNull() ?: 0
                        val outputVar = normalizeVariableName(block.config["outputVariable"].orEmpty())
                        val result = cropImage(path, x, y, w, h)
                        FlowLog.add(flow.name, "Crop($path, $x,$y,${w}x$h) -> ${result.take(80)}")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.PICK_COLOR -> {
                        val path = substituteVariables(block.config["imagePath"].orEmpty(), variables)
                        val x = substituteVariables(block.config["x"].orEmpty(), variables).toIntOrNull() ?: 0
                        val y = substituteVariables(block.config["y"].orEmpty(), variables).toIntOrNull() ?: 0
                        val outputVar = normalizeVariableName(block.config["outputVariable"].orEmpty())
                        val result = pickColor(path, x, y)
                        FlowLog.add(flow.name, "PickColor($path, $x,$y) -> $result")
                        if (outputVar.isNotBlank()) variables[outputVar] = result
                    }
                    BlockType.FIND_IMAGE -> {
                        val templatePath = substituteVariables(block.config["templatePath"].orEmpty(), variables)
                        val sourcePath = substituteVariables(block.config["sourcePath"].orEmpty(), variables)
                        val threshold = substituteVariables(block.config["threshold"].orEmpty(), variables)
                            .toDoubleOrNull() ?: 0.8
                        val foundVar = normalizeVariableName(block.config["outputFoundVariable"].orEmpty())
                        val xVar = normalizeVariableName(block.config["outputXVariable"].orEmpty())
                        val yVar = normalizeVariableName(block.config["outputYVariable"].orEmpty())

                        when (val outcome = findImage(templatePath, sourcePath, threshold)) {
                            is FindImageOutcome.Found -> {
                                FlowLog.add(
                                    flow.name,
                                    "Find Image($templatePath) -> found at (${outcome.x},${outcome.y}) score=${"%.2f".format(outcome.score)}"
                                )
                                if (foundVar.isNotBlank()) variables[foundVar] = "true"
                                if (xVar.isNotBlank()) variables[xVar] = outcome.x.toString()
                                if (yVar.isNotBlank()) variables[yVar] = outcome.y.toString()
                            }
                            is FindImageOutcome.NotFound -> {
                                FlowLog.add(flow.name, "Find Image($templatePath) -> not found")
                                if (foundVar.isNotBlank()) variables[foundVar] = "false"
                            }
                            is FindImageOutcome.Error -> {
                                FlowLog.add(flow.name, "Find Image($templatePath) -> Error: ${outcome.message}", LogLevel.ERROR)
                                if (foundVar.isNotBlank()) variables[foundVar] = "false"
                            }
                        }
                    }
                    else -> {}
                }
            }
            BlockCategory.TRIGGER -> { }
        }

        for (next in flow.outgoingFrom(block.id, "output")) {
            walk(flow, next, visited, variables)
        }
    }

    /**
     * Prefers the Accessibility Service screenshot API (Android 11+, no
     * MediaProjection consent, no persistent cast icon) when a connected
     * gesture service is available; falls back to the MediaProjection-based
     * ScreenCaptureService otherwise (older Android, or gestures not enabled).
     */
    private suspend fun captureScreenshotPreferred(): String {
        val gestureService = GestureAccessibilityService.instance
        if (gestureService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val result = gestureService.captureScreenshotViaAccessibility(context.filesDir)
            if (!result.startsWith("Error")) return result
            // Fall through to MediaProjection if the accessibility route failed.
        }
        return ScreenCaptureService.instance?.captureScreenshot()
            ?: "Error: screenshot not enabled — enable Gestures (preferred, no icon) or tap Enable Screenshot on the flow list screen"
    }

    private suspend fun runShellCommand(command: String): String = withContext(Dispatchers.IO) {
        if (command.isBlank()) return@withContext "Error: no command given"
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .directory(context.filesDir)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                "Error: command timed out after 10s — commands like 'ping' run forever unless you add a stop count (e.g. ping -c 4 host)"
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
            val image = InputImage.fromFilePath(context, resolveImageUri(path))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).await().text
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun cropImage(path: String, x: Int, y: Int, width: Int, height: Int): String = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext "Error: no image path given"
        try {
            val original = context.contentResolver.openInputStream(resolveImageUri(path))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext "Error: couldn't decode image"

            val safeX = x.coerceIn(0, (original.width - 1).coerceAtLeast(0))
            val safeY = y.coerceIn(0, (original.height - 1).coerceAtLeast(0))
            val safeW = width.coerceIn(1, (original.width - safeX).coerceAtLeast(1))
            val safeH = height.coerceIn(1, (original.height - safeY).coerceAtLeast(1))

            val cropped = Bitmap.createBitmap(original, safeX, safeY, safeW, safeH)
            val file = File(context.filesDir, "crop_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file.absolutePath
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun pickColor(path: String, x: Int, y: Int): String = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext "Error: no image path given"
        try {
            val bitmap = context.contentResolver.openInputStream(resolveImageUri(path))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext "Error: couldn't decode image"

            val safeX = x.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
            val safeY = y.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
            val pixel = bitmap.getPixel(safeX, safeY)
            String.format("#%06X", 0xFFFFFF and pixel)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun findImage(templatePath: String, sourcePathRaw: String, threshold: Double): FindImageOutcome =
        withContext(Dispatchers.Default) {
            if (templatePath.isBlank()) return@withContext FindImageOutcome.Error("no template image given")
            try {
                val templateBitmap = context.contentResolver.openInputStream(resolveImageUri(templatePath))?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext FindImageOutcome.Error("couldn't decode template image")

                val sourceBitmap = if (sourcePathRaw.isNotBlank()) {
                    context.contentResolver.openInputStream(resolveImageUri(sourcePathRaw))?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@withContext FindImageOutcome.Error("couldn't decode source image")
                } else {
                    val screenshotResult = captureScreenshotPreferred()
                    if (screenshotResult.startsWith("Error")) {
                        return@withContext FindImageOutcome.Error(screenshotResult)
                    }
                    BitmapFactory.decodeFile(screenshotResult)
                        ?: return@withContext FindImageOutcome.Error("couldn't decode screenshot")
                }

                val match = ImageMatcher.findTemplate(sourceBitmap, templateBitmap, threshold)
                if (match.found) {
                    FindImageOutcome.Found(match.x, match.y, match.score)
                } else {
                    FindImageOutcome.NotFound
                }
            } catch (e: Exception) {
                FindImageOutcome.Error(e.message ?: "unknown error")
            }
        }

    private fun resolveImageUri(path: String): Uri = when {
        path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
        else -> Uri.fromFile(File(path))
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

    private fun normalizeVariableName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\${") && trimmed.endsWith("}")) {
            trimmed.substring(2, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun substituteVariables(text: String, variables: Map<String, String>): String {
        val funcRegex = Regex("""\$\{(\w+)\(([^)]*)\)\}""")
        val afterFunctions = funcRegex.replace(text) { match ->
            val funcName = match.groupValues[1]
            val rawArgs = match.groupValues[2]
            val args = if (rawArgs.isBlank()) {
                emptyList()
            } else {
                rawArgs.split(",").map { arg ->
                    val trimmed = arg.trim().removeSurrounding("\"")
                    variables[trimmed] ?: trimmed
                }
            }
            callFunction(funcName, args)
        }

        val varRegex = Regex("""\$\{(\w+)\}""")
        return varRegex.replace(afterFunctions) { match -> variables[match.groupValues[1]] ?: match.value }
    }

    private fun callFunction(name: String, args: List<String>): String = try {
        when (name) {
            "round" -> {
                val num = args.getOrNull(0)?.toDoubleOrNull()
                if (num == null) {
                    "Error: round(number, decimals?) needs a numeric first argument"
                } else {
                    val decimals = args.getOrNull(1)?.toIntOrNull() ?: 0
                    val factor = Math.pow(10.0, decimals.toDouble())
                    val rounded = (num * factor).roundToLong() / factor
                    if (decimals <= 0) rounded.toLong().toString() else rounded.toString()
                }
            }
            "split" -> {
                val text = args.getOrNull(0) ?: ""
                val delimiter = args.getOrNull(1) ?: ","
                val index = args.getOrNull(2)?.toIntOrNull() ?: 0
                text.split(delimiter).getOrNull(index) ?: ""
            }
            "concat" -> args.joinToString("")
            "upper" -> (args.getOrNull(0) ?: "").uppercase()
            "lower" -> (args.getOrNull(0) ?: "").lowercase()
            "length" -> (args.getOrNull(0) ?: "").length.toString()
            "currentTime" -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            "currentDate" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            "currentOpenApp" -> currentForegroundApp()
            else -> "Error: unknown function $name"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private fun currentForegroundApp(): String = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - TimeUnit.MINUTES.toMillis(1)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            ?: "Error: enable Usage Access for Flowmate in Settings"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
