package com.rui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data class Bullet(val text: String, val indent: Int) : Block
    data class Numbered(val n: String, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Para(val text: String) : Block
}

private fun parse(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = src.lines()
    var i = 0
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += Block.Para(para.toString().trim()); para.clear() }
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {                       // also handles a fence that is still streaming
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                out += Block.Code(lang, code.toString().trimEnd())
            }
            t.startsWith("#") -> {
                flush()
                val lvl = t.takeWhile { it == '#' }.length.coerceIn(1, 4)
                out += Block.Heading(lvl, t.dropWhile { it == '#' }.trim())
            }
            Regex("^[-*•]\\s+.*").matches(t) ->
                { flush(); out += Block.Bullet(t.drop(1).trim(), (line.length - line.trimStart().length) / 2) }
            Regex("^\\d+[.)]\\s+.*").matches(t) ->
                { flush(); out += Block.Numbered(t.takeWhile { it.isDigit() } + ".", t.dropWhile { it.isDigit() }.drop(1).trim()) }
            t.startsWith(">") -> { flush(); out += Block.Quote(t.removePrefix(">").trim()) }
            t.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
        }
        i++
    }
    flush()
    return out
}

private fun inline(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (text.startsWith("**", i)) {
            val e = text.indexOf("**", i + 2)
            if (e > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline(text.substring(i + 2, e), codeBg)) }; i = e + 2; continue }
        }
        if (c == '`') {
            val e = text.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 13.sp)) { append(" ${text.substring(i + 1, e)} ") }
                i = e + 1; continue
            }
        }
        if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] != ' ') {
            val e = text.indexOf(c, i + 1)
            if (e > 0 && text[e - 1] != ' ') {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(text.substring(i + 1, e), codeBg)) }
                i = e + 1; continue
            }
        }
        append(c); i++
    }
}

/** Zero-dependency markdown renderer: headings, bold/italic, inline + fenced code, lists, quotes. */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val blocks = remember(text) { parse(text) }
    val codeBg = cs.surfaceContainerHigh
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Heading -> Text(
                    inline(b.text, codeBg),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (24 - b.level * 2).sp),
                    color = cs.onSurface,
                )
                is Block.Para -> Text(inline(b.text, codeBg), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                is Block.Bullet -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                    Text("•", color = cs.primary, modifier = Modifier.width(18.dp))
                    Text(inline(b.text, codeBg), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                }
                is Block.Numbered -> Row {
                    Text(b.n, color = cs.primary, modifier = Modifier.width(26.dp))
                    Text(inline(b.text, codeBg), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                }
                is Block.Quote -> Row {
                    Column(Modifier.width(3.dp).background(cs.tertiary)) { Text(" ", fontSize = 18.sp) }
                    Text(inline(b.text, codeBg), Modifier.padding(start = 10.dp), color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic)
                }
                is Block.Code -> CodeBlock(b.lang, b.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF070714))) {
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(start = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy", fontSize = 12.sp) }
        }
        Text(
            code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFD6DBFF),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
}
