package com.example.automateclone.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automateclone.model.BlockCategory
import com.example.automateclone.model.BlockType

private fun currentLineStart(text: String, cursor: Int): Int {
    val idx = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
    return if (idx == -1) 0 else idx + 1
}

private val TYPE_LINE_REGEX = Regex("""^\s*(trigger|action|logic)\s+([A-Za-z_]*)$""")
private val KEYWORD_LINE_REGEX = Regex("""^\s*(\w+)$""")
private val TRAILING_WORD_REGEX = Regex("""[\w.]*$""")

private fun collectAliases(text: String): List<String> =
    Regex("""\bas\s+([A-Za-z0-9_]+)""").findAll(text).map { it.groupValues[1] }.distinct().toList()

private sealed class CodeSuggestion(val insertText: String, val display: String) {
    class Keyword(word: String) : CodeSuggestion("$word ", word)
    class BlockTypeName(name: String, display: String) : CodeSuggestion(name, display)
    class Alias(name: String) : CodeSuggestion(name, name)
}

private fun computeSuggestions(value: TextFieldValue): List<CodeSuggestion> {
    val cursor = value.selection.start
    val lineStart = currentLineStart(value.text, cursor)
    val beforeCursorOnLine = value.text.substring(lineStart, cursor)

    TYPE_LINE_REGEX.find(beforeCursorOnLine)?.let { m ->
        val keyword = m.groupValues[1]
        val partial = m.groupValues[2]
        val category = when (keyword) {
            "trigger" -> BlockCategory.TRIGGER
            "action" -> BlockCategory.ACTION
            else -> BlockCategory.LOGIC
        }
        return BlockType.entries
            .filter { it.category == category && it.name.startsWith(partial, ignoreCase = true) }
            .map { CodeSuggestion.BlockTypeName(it.name, "${it.name} — ${it.displayName}") }
    }

    KEYWORD_LINE_REGEX.find(beforeCursorOnLine)?.let { m ->
        val partial = m.groupValues[1]
        val keywords = listOf("trigger", "action", "logic")
            .filter { it.startsWith(partial, ignoreCase = true) }
        if (keywords.isNotEmpty()) {
            return keywords.map { CodeSuggestion.Keyword(it) }
        }
    }

    val partial = TRAILING_WORD_REGEX.find(beforeCursorOnLine)?.value.orEmpty()
    val basePartial = partial.substringBefore(".")
    if (partial.isNotEmpty()) {
        val aliases = collectAliases(value.text).filter { it.startsWith(basePartial, ignoreCase = true) }
        if (aliases.isNotEmpty()) {
            return aliases.map { CodeSuggestion.Alias(it) }
        }
    }

    return emptyList()
}

private fun applySuggestion(value: TextFieldValue, suggestion: CodeSuggestion): TextFieldValue {
    val cursor = value.selection.start
    val lineStart = currentLineStart(value.text, cursor)
    val beforeCursorOnLine = value.text.substring(lineStart, cursor)

    val wordStartOnLine = TRAILING_WORD_REGEX.find(beforeCursorOnLine)?.range?.first ?: beforeCursorOnLine.length
    val wordStart = lineStart + wordStartOnLine

    val prefix = value.text.substring(0, wordStart)
    val suffix = value.text.substring(cursor)
    val newText = prefix + suggestion.insertText + suffix
    return TextFieldValue(newText, TextRange(prefix.length + suggestion.insertText.length))
}

@Composable
fun CodeEditorScreen(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    error: String?,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = computeSuggestions(value)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Edit the flow as code, then tap Apply to update the canvas.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            ) {
                suggestions.take(12).forEach { s ->
                    TextButton(onClick = { onValueChange(applySuggestion(value, s)) }) {
                        Text(s.display, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply to Flow")
        }
    }
}
