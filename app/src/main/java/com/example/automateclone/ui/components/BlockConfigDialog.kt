package com.example.automateclone.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automateclone.actions.OverlayPicker
import com.example.automateclone.actions.OverlayPickerService
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockType
import kotlin.math.roundToInt

private val BUILTIN_FUNCTIONS = listOf(
    "round" to "round(number, decimals)",
    "split" to "split(text, delimiter, index)",
    "concat" to "concat(a, b, ...)",
    "upper" to "upper(text)",
    "lower" to "lower(text)",
    "length" to "length(text)",
    "currentTime" to "currentTime()",
    "currentDate" to "currentDate()",
    "currentOpenApp" to "currentOpenApp()"
)

private val COMMON_SHELL_COMMANDS = listOf(
    "ls" to "list files",
    "cat" to "print file contents",
    "echo" to "print text",
    "pwd" to "working directory",
    "whoami" to "current user",
    "date" to "current date/time",
    "ps" to "list processes",
    "df" to "disk free space",
    "du" to "disk usage",
    "mkdir" to "make directory",
    "rm" to "remove file",
    "cp" to "copy file",
    "mv" to "move/rename file",
    "chmod" to "change permissions",
    "grep" to "search text",
    "find" to "find files",
    "sleep" to "pause N seconds",
    "ping" to "network ping (add -c 4 to stop)",
    "id" to "user/group ids",
    "uname" to "system info",
    "top" to "process snapshot",
    "kill" to "send signal to process",
    "touch" to "create/update file",
    "head" to "first lines of file",
    "tail" to "last lines of file",
    "wc" to "word/line count",
    "sort" to "sort lines",
    "uniq" to "filter duplicate lines",
    "getprop" to "read system property",
    "settings" to "read/write Android settings"
)

private val COMMON_OPERATORS = listOf(
    "&&", "||", "==", "!=", ">=", "<=", ">", "<", "+", "-", "*", "/", "!"
)

private fun currentPartialVariable(value: TextFieldValue): String? {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return null
    val segment = before.substring(lastOpen + 2)
    if (segment.contains("}") || segment.contains("$") || segment.contains(" ") || segment.contains("(")) return null
    return segment
}

/** True when the cursor sits inside the unclosed parentheses of a function
 * call like ${round(|)} — i.e. an unmatched '(' after the most recent ${. */
private fun isInsideFunctionArgs(value: TextFieldValue): Boolean {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return false
    val segment = before.substring(lastOpen + 2)
    if (segment.contains("}")) return false
    val openParenIdx = segment.indexOf("(")
    if (openParenIdx == -1) return false
    var depth = 0
    for (c in segment.substring(openParenIdx)) {
        if (c == '(') depth++
        if (c == ')') depth--
    }
    return depth > 0
}

/** The partial argument word being typed, delimited by the last '(' or ','. */
private fun currentArgPartial(value: TextFieldValue): String? {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastComma = before.lastIndexOf(",")
    val lastParen = before.lastIndexOf("(")
    val start = maxOf(lastComma, lastParen)
    if (start == -1) return null
    val partial = before.substring(start + 1).trimStart()
    if (partial.contains(" ") || partial.contains(")") || partial.contains("\"")) return null
    return partial
}

private fun currentShellCommandWord(value: TextFieldValue): String? {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    if (before.contains(" ") || before.contains("\t")) return null
    return before
}

private fun insertVariableAtCursor(value: TextFieldValue, varName: String): TextFieldValue {
    val cursor = value.selection.start
    val insertion = "\${$varName}"
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    return TextFieldValue(newText, TextRange(cursor + insertion.length))
}

private fun insertFunctionAtCursor(value: TextFieldValue, funcName: String): TextFieldValue {
    val cursor = value.selection.start
    val insertion = "\${$funcName()}"
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    val newCursor = cursor + "\${$funcName(".length
    return TextFieldValue(newText, TextRange(newCursor))
}

private fun completeVariable(value: TextFieldValue, fullName: String): TextFieldValue {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return value
    val prefix = value.text.substring(0, lastOpen + 2)
    val suffix = value.text.substring(cursor)
    val newText = prefix + fullName + "}" + suffix
    return TextFieldValue(newText, TextRange(prefix.length + fullName.length + 1))
}

private fun completeFunction(value: TextFieldValue, funcName: String): TextFieldValue {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastOpen = before.lastIndexOf("\${")
    if (lastOpen == -1) return value
    val prefix = value.text.substring(0, lastOpen + 2)
    val suffix = value.text.substring(cursor)
    val newText = prefix + funcName + "()}" + suffix
    return TextFieldValue(newText, TextRange(prefix.length + funcName.length + 1))
}

private fun completeShellCommand(value: TextFieldValue, command: String): TextFieldValue {
    val cursor = value.selection.start
    val suffix = value.text.substring(cursor).trimStart()
    val newText = "$command $suffix"
    return TextFieldValue(newText, TextRange(command.length + 1))
}

private fun completeArgVariable(value: TextFieldValue, name: String): TextFieldValue {
    val cursor = value.selection.start
    val before = value.text.substring(0, cursor)
    val lastComma = before.lastIndexOf(",")
    val lastParen = before.lastIndexOf("(")
    val start = maxOf(lastComma, lastParen)
    if (start == -1) return value
    val prefix = value.text.substring(0, start + 1)
    val suffix = value.text.substring(cursor)
    val newText = prefix + name + suffix
    return TextFieldValue(newText, TextRange(prefix.length + name.length))
}

private fun insertOperatorAtCursor(value: TextFieldValue, operator: String): TextFieldValue {
    val cursor = value.selection.start
    val insertion = " $operator "
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    return TextFieldValue(newText, TextRange(cursor + insertion.length))
}

private val ENUM_FIELD_OPTIONS: Map<Pair<BlockType, String>, List<Pair<String, String>>> = mapOf(
    (BlockType.IF_CONDITION to "operator") to listOf(
        "equals" to "Equals (=)",
        "notEquals" to "Not equals (≠)",
        "contains" to "Contains",
        "greaterThan" to "Greater than (>)",
        "lessThan" to "Less than (<)"
    ),
    (BlockType.BATTERY_LEVEL to "direction") to listOf(
        "above" to "At or above threshold",
        "below" to "At or below threshold"
    ),
    (BlockType.DEVICE_CHARGING to "state") to listOf(
        "charging" to "Started charging",
        "not_charging" to "Stopped charging"
    ),
    (BlockType.SCREEN_STATE to "state") to listOf(
        "on" to "Screen turned on",
        "off" to "Screen turned off"
    )
)

@Composable
private fun ExpressionField(
    fieldKey: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isCode: Boolean,
    onEnterCodeMode: () -> Unit,
    onExitCodeMode: () -> Unit,
    availableVariables: List<String>,
    fxMenuOpen: Boolean,
    onFxMenuOpenChange: (Boolean) -> Unit,
    shellSuggestionsFor: String? = null
) {
    val partial = if (isCode) currentPartialVariable(value) else null
    val variableSuggestions = if (partial != null) {
        availableVariables.filter { it.startsWith(partial, ignoreCase = true) }
    } else emptyList()
    val functionSuggestions = if (partial != null) {
        BUILTIN_FUNCTIONS.filter { it.first.startsWith(partial, ignoreCase = true) }
    } else emptyList()
    val insideArgs = isCode && isInsideFunctionArgs(value)
    val argPartial = if (insideArgs) currentArgPartial(value) else null
    val argVariableSuggestions = if (argPartial != null) {
        availableVariables.filter { it.startsWith(argPartial, ignoreCase = true) }
    } else emptyList()
    val shellCommandSuggestions = if (isCode && shellSuggestionsFor == fieldKey) {
        currentShellCommandWord(value)?.takeIf { it.isNotEmpty() }?.let { word ->
            COMMON_SHELL_COMMANDS.filter { it.first.startsWith(word, ignoreCase = true) }
        } ?: emptyList()
    } else emptyList()

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(fieldKey) },
                textStyle = if (isCode) {
                    LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                } else {
                    LocalTextStyle.current
                },
                modifier = Modifier.weight(1f)
            )
            if (isCode) {
                Box {
                    TextButton(onClick = { onFxMenuOpenChange(true) }) { Text("fx") }
                    DropdownMenu(
                        expanded = fxMenuOpen,
                        onDismissRequest = { onFxMenuOpenChange(false) }
                    ) {
                        if (availableVariables.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Variables", fontSize = 11.sp) }, onClick = {}, enabled = false)
                            availableVariables.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onValueChange(insertVariableAtCursor(value, name))
                                        onFxMenuOpenChange(false)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(text = { Text("Functions", fontSize = 11.sp) }, onClick = {}, enabled = false)
                        BUILTIN_FUNCTIONS.forEach { (name, signature) ->
                            DropdownMenuItem(
                                text = { Text(signature) },
                                onClick = {
                                    onValueChange(insertFunctionAtCursor(value, name))
                                    onFxMenuOpenChange(false)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Switch to simple value") },
                            onClick = {
                                onExitCodeMode()
                                onFxMenuOpenChange(false)
                            }
                        )
                    }
                }
            } else {
                TextButton(onClick = onEnterCodeMode) { Text("fx") }
            }
        }
        if (isCode && (shellCommandSuggestions.isNotEmpty() || variableSuggestions.isNotEmpty() || functionSuggestions.isNotEmpty() || argVariableSuggestions.isNotEmpty())) {
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                shellCommandSuggestions.forEach { (name, desc) ->
                    TextButton(
                        onClick = { onValueChange(completeShellCommand(value, name)) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text("$name — $desc", fontSize = 12.sp) }
                }
                argVariableSuggestions.forEach { name ->
                    TextButton(
                        onClick = { onValueChange(completeArgVariable(value, name)) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text(name, fontSize = 12.sp) }
                }
                variableSuggestions.forEach { name ->
                    TextButton(
                        onClick = { onValueChange(completeVariable(value, name)) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text(name, fontSize = 12.sp) }
                }
                functionSuggestions.forEach { (name, _) ->
                    TextButton(
                        onClick = { onValueChange(completeFunction(value, name)) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text("$name()", fontSize = 12.sp) }
                }
            }
        }
        if (isCode) {
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                COMMON_OPERATORS.forEach { op ->
                    TextButton(
                        onClick = { onValueChange(insertOperatorAtCursor(value, op)) },
                        modifier = Modifier.padding(end = 2.dp)
                    ) { Text(op, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

private fun isPositionGroupStart(type: BlockType, key: String): Boolean =
    ((type == BlockType.TAP || type == BlockType.LONG_PRESS) && key == "x") ||
        (type == BlockType.SWIPE && key == "startX")

private fun isPositionGroupContinuation(type: BlockType, key: String): Boolean =
    ((type == BlockType.TAP || type == BlockType.LONG_PRESS) && key == "y") ||
        (type == BlockType.SWIPE && key in listOf("startY", "endX", "endY"))

private fun positionGroupKeys(type: BlockType): List<String> =
    if (type == BlockType.SWIPE) listOf("startX", "startY", "endX", "endY") else listOf("x", "y")

@Composable
fun BlockConfigDialog(
    block: Block,
    availableVariables: List<String> = emptyList(),
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fields = remember {
        mutableStateMapOf<String, TextFieldValue>().apply {
            block.config.forEach { (k, v) -> put(k, TextFieldValue(v)) }
        }
    }
    var codeModeKeys by remember {
        mutableStateOf(block.config.filterValues { it.contains("\${") }.keys.toSet())
    }
    var manualEntryKeys by remember { mutableStateOf(setOf<String>()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var colorPickerKey by remember { mutableStateOf<String?>(null) }
    var fxMenuKey by remember { mutableStateOf<String?>(null) }
    var enumMenuKey by remember { mutableStateOf<String?>(null) }
    var pendingImagePickKey by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val key = pendingImagePickKey
        if (uri != null && key != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Some providers don't support a persistable grant; the path
                // still works for this session even if this fails.
            }
            fields[key] = TextFieldValue(uri.toString())
        }
        pendingImagePickKey = null
    }

    if (showAppPicker) {
        AppPickerDialog(
            onPick = { packageName, _ ->
                fields["packageName"] = TextFieldValue(packageName)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
        return
    }

    colorPickerKey?.let { key ->
        ColorPickerDialog(
            onPick = { hex ->
                fields[key] = TextFieldValue(hex)
                colorPickerKey = null
            },
            onDismiss = { colorPickerKey = null }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${block.type.displayName}") },
        text = {
            Column {
                block.type.configKeys.forEach { key ->
                    val enumOptions = ENUM_FIELD_OPTIONS[block.type to key]
                    val groupKeys = positionGroupKeys(block.type)
                    val groupActive = groupKeys.any { it in manualEntryKeys }
                    when {
                        isPositionGroupContinuation(block.type, key) && !groupActive -> {
                            // Skip — rendered together with the group's first key below,
                            // unless the group has been switched to manual entry.
                        }
                        isPositionGroupStart(block.type, key) && !groupActive -> {
                            val isSwipe = block.type == BlockType.SWIPE
                            val label = if (isSwipe) {
                                val sx = fields["startX"]?.text.orEmpty()
                                val sy = fields["startY"]?.text.orEmpty()
                                val ex = fields["endX"]?.text.orEmpty()
                                val ey = fields["endY"]?.text.orEmpty()
                                if (sx.isBlank() || sy.isBlank() || ex.isBlank() || ey.isBlank()) {
                                    "Pick start & end points on screen"
                                } else {
                                    "($sx, $sy) -> ($ex, $ey)"
                                }
                            } else {
                                val cx = fields["x"]?.text.orEmpty()
                                val cy = fields["y"]?.text.orEmpty()
                                if (cx.isBlank() || cy.isBlank()) "Pick location on screen" else "Location: ($cx, $cy)"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        if (!Settings.canDrawOverlays(context)) {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        } else {
                                            OverlayPicker.onPicked = { points ->
                                                if (isSwipe) {
                                                    val p1 = points.getOrNull(0)
                                                    val p2 = points.getOrNull(1)
                                                    if (p1 != null) {
                                                        fields["startX"] = TextFieldValue(p1.first.roundToInt().toString())
                                                        fields["startY"] = TextFieldValue(p1.second.roundToInt().toString())
                                                    }
                                                    if (p2 != null) {
                                                        fields["endX"] = TextFieldValue(p2.first.roundToInt().toString())
                                                        fields["endY"] = TextFieldValue(p2.second.roundToInt().toString())
                                                    }
                                                } else {
                                                    points.firstOrNull()?.let { p1 ->
                                                        fields["x"] = TextFieldValue(p1.first.roundToInt().toString())
                                                        fields["y"] = TextFieldValue(p1.second.roundToInt().toString())
                                                    }
                                                }
                                            }
                                            OverlayPicker.onCancelled = { }
                                            context.startService(
                                                Intent(context, OverlayPickerService::class.java)
                                                    .putExtra(OverlayPickerService.EXTRA_POINTS_NEEDED, if (isSwipe) 2 else 1)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                ) { Text(label) }
                                TextButton(onClick = { manualEntryKeys = manualEntryKeys + groupKeys }) { Text("Manual") }
                            }
                        }
                        block.type == BlockType.LAUNCH_APP && key == "packageName" && key !in manualEntryKeys -> {
                            val currentPackage = fields[key]?.text.orEmpty()
                            val label = remember(currentPackage) {
                                if (currentPackage.isBlank()) {
                                    null
                                } else {
                                    try {
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(currentPackage, 0)
                                        ).toString()
                                    } catch (e: Exception) {
                                        currentPackage
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { showAppPicker = true },
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                ) {
                                    Text(label ?: "Choose an app")
                                }
                                TextButton(onClick = { manualEntryKeys = manualEntryKeys + key }) { Text("Manual") }
                            }
                        }
                        key == "colorHex" || key == "color" -> {
                            val current = fields[key]?.text.orEmpty()
                            OutlinedButton(
                                onClick = { colorPickerKey = key },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(parseColorSafely(current) ?: Color.Gray)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (current.isBlank()) "Choose a color (optional)" else current)
                                }
                            }
                        }
                        key == "imagePath" && key !in manualEntryKeys -> {
                            val current = fields[key]?.text.orEmpty()
                            val displayName = remember(current) {
                                if (current.isBlank()) {
                                    null
                                } else {
                                    try {
                                        Uri.parse(current).lastPathSegment ?: current
                                    } catch (e: Exception) {
                                        current
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        pendingImagePickKey = key
                                        imagePickerLauncher.launch(arrayOf("image/*"))
                                    },
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                ) {
                                    Text(displayName ?: "Choose an image")
                                }
                                TextButton(onClick = { manualEntryKeys = manualEntryKeys + key }) { Text("Manual") }
                            }
                        }
                        enumOptions != null -> {
                            val currentValue = fields[key]?.text.orEmpty()
                            val currentLabel = enumOptions.find { it.first == currentValue }?.second
                            Box(Modifier.padding(vertical = 4.dp)) {
                                OutlinedButton(
                                    onClick = { enumMenuKey = key },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(currentLabel ?: "Choose $key")
                                }
                                DropdownMenu(
                                    expanded = enumMenuKey == key,
                                    onDismissRequest = { enumMenuKey = null }
                                ) {
                                    enumOptions.forEach { (optionValue, optionLabel) ->
                                        DropdownMenuItem(
                                            text = { Text(optionLabel) },
                                            onClick = {
                                                fields[key] = TextFieldValue(optionValue)
                                                enumMenuKey = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val value = fields[key] ?: TextFieldValue("")
                            val isCode = key in codeModeKeys
                            val isManualPickerField = key in manualEntryKeys && (
                                key == "imagePath" ||
                                    (block.type == BlockType.LAUNCH_APP && key == "packageName") ||
                                    (isPositionGroupStart(block.type, key))
                            )

                            Column {
                                ExpressionField(
                                    fieldKey = key,
                                    value = value,
                                    onValueChange = { fields[key] = it },
                                    isCode = isCode,
                                    onEnterCodeMode = {
                                        codeModeKeys = codeModeKeys + key
                                        fxMenuKey = key
                                    },
                                    onExitCodeMode = { codeModeKeys = codeModeKeys - key },
                                    availableVariables = availableVariables,
                                    fxMenuOpen = fxMenuKey == key,
                                    onFxMenuOpenChange = { open -> fxMenuKey = if (open) key else null },
                                    shellSuggestionsFor = if (block.type == BlockType.SHELL_COMMAND && key == "command") key else null
                                )
                                if (isManualPickerField) {
                                    TextButton(
                                        onClick = {
                                            manualEntryKeys = if (isPositionGroupStart(block.type, key)) {
                                                manualEntryKeys - groupKeys.toSet()
                                            } else {
                                                manualEntryKeys - key
                                            }
                                        },
                                        modifier = Modifier.offset(y = (-8).dp)
                                    ) { Text("Use picker instead") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fields.mapValues { it.value.text }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
