package com.consensus.app.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Minimal Markdown renderer: headings, bullet/numbered lists, **bold**,
 * *italic* and `code`. Enough to make model output readable without a
 * Markdown library dependency.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val base = MaterialTheme.typography.bodyMedium
    SelectionContainer {
        Text(text = remember(text) { renderMarkdown(text) }, style = base, modifier = modifier)
    }
}

internal fun renderMarkdown(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.replace("\r\n", "\n").split("\n")
    var inCode = false
    lines.forEachIndexed { i, raw ->
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            inCode = !inCode
            return@forEachIndexed
        }
        if (inCode) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) { append(raw) }
            if (i < lines.lastIndex) append('\n')
            return@forEachIndexed
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$").find(line)
        val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$").find(line)
        when {
            heading != null -> {
                val level = heading.groupValues[1].length
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (level <= 2) 17.sp else 15.sp)) {
                    appendInline(heading.groupValues[2])
                }
            }
            bullet != null -> {
                val indent = bullet.groupValues[1].length / 2
                append("    ".repeat(indent) + "• ")
                appendInline(bullet.groupValues[2])
            }
            numbered != null -> {
                val indent = numbered.groupValues[1].length / 2
                append("    ".repeat(indent) + numbered.groupValues[2] + ". ")
                appendInline(numbered.groupValues[3])
            }
            else -> appendInline(line)
        }
        if (i < lines.lastIndex) append('\n')
    }
}

private val inlinePattern = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`]+)`)|(\\*(?!\\s)(.+?)(?<!\\s)\\*)|(\\[([^\\]]+)]\\(([^)]+)\\))")

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(text: String) {
    var last = 0
    inlinePattern.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        when {
            m.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
            m.groups[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) { append(m.groupValues[4]) }
            m.groups[6] != null -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(m.groupValues[6]) }
            m.groups[8] != null -> {
                append(m.groupValues[8])
                append(" (")
                append(m.groupValues[9])
                append(")")
            }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
