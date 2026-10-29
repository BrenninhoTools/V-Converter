package com.brenninhotools.vconverter.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.theme.Funkin
import com.brenninhotools.vconverter.overlay.CodePreviewText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val COLLAPSED_LINES = 18
private const val EXPANDED_LINES = 800

private val keywords = setOf(
    "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "else", "enum", "extends", "false",
    "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "new", "null", "override",
    "package", "private", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typedef",
    "var", "while", "and", "elseif", "end", "local", "nil", "not", "or", "repeat", "then", "until", "goto"
)

private object CodeColors {
    val keyword = Funkin.Left
    val string = Funkin.Up
    val number = Funkin.Warning
    val comment = Funkin.Muted.copy(alpha = 0.75f)
    val type = Funkin.Down
    val call = Funkin.Lavender
    val plain = Funkin.Text
}

fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = code.length
    fun emit(text: String, color: Color, italic: Boolean = false, bold: Boolean = false) {
        withStyle(SpanStyle(color = color, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)) { append(text) }
    }
    while (i < n) {
        val c = code[i]
        when {
            (c == '/' && code.getOrNull(i + 1) == '/') || (c == '-' && code.getOrNull(i + 1) == '-' && code.getOrNull(i + 2) != '[') -> {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                emit(code.substring(i, end), CodeColors.comment, italic = true)
                i = end
            }
            c == '/' && code.getOrNull(i + 1) == '*' -> {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                emit(code.substring(i, end), CodeColors.comment, italic = true)
                i = end
            }
            c == '"' || c == '\'' -> {
                var j = i + 1
                while (j < n && code[j] != c && code[j] != '\n') {
                    if (code[j] == '\\') j++
                    j++
                }
                val end = minOf(j + 1, n)
                emit(code.substring(i, end), CodeColors.string)
                i = end
            }
            c.isDigit() -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' && code.getOrNull(j + 1)?.isDigit() == true)) j++
                emit(code.substring(i, j), CodeColors.number)
                i = j
            }
            c.isLetter() || c == '_' -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                var k = j
                while (k < n && code[k] == ' ') k++
                when {
                    word in keywords -> emit(word, CodeColors.keyword, bold = true)
                    word.first().isUpperCase() -> emit(word, CodeColors.type)
                    code.getOrNull(k) == '(' -> emit(word, CodeColors.call)
                    else -> emit(word, CodeColors.plain)
                }
                i = j
            }
            else -> {
                var j = i + 1
                while (j < n && !code[j].isLetterOrDigit() && code[j] != '_' && code[j] != '"' && code[j] != '\'' && code[j] != '/' && code[j] != '-') j++
                emit(code.substring(i, j), CodeColors.plain)
                i = j
            }
        }
    }
}

private data class PreparedCode(val body: AnnotatedString, val gutter: String, val total: Int, val shown: Int)

@Composable
fun CodeView(code: String, title: String = "Preview", accent: Color = Funkin.Down) {
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(code.length, code.hashCode()) { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val prepared by produceState<PreparedCode?>(null, code, expanded) {
        value = withContext(Dispatchers.Default) {
            val preview = CodePreviewText.prepare(code, if (expanded) EXPANDED_LINES else COLLAPSED_LINES)
            PreparedCode(highlight(preview.text), (1..preview.shownLines).joinToString("\n"), preview.totalLines, preview.shownLines)
        }
    }
    val total = prepared?.total ?: 0
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Funkin.Ink)
            .border(1.5.dp, Funkin.Outline, shape)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Funkin.Surface)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(Funkin.Right, Funkin.Warning, Funkin.Up).forEach { dot ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot.copy(alpha = 0.8f)))
                Spacer(Modifier.width(5.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Funkin.Muted, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$total lines", style = MaterialTheme.typography.labelSmall, color = Funkin.Muted.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (copied) Funkin.Up.copy(alpha = 0.18f) else accent.copy(alpha = 0.12f))
                    .clickable {
                        clipboard.setText(AnnotatedString(code.trimEnd()))
                        copied = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_copy),
                    contentDescription = "Copy code",
                    tint = if (copied) Funkin.Up else accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (copied) "COPIED" else "COPY", style = MaterialTheme.typography.labelSmall, color = if (copied) Funkin.Up else accent)
            }
        }
        val ready = prepared
        if (ready == null) {
            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted, modifier = Modifier.padding(14.dp))
        } else Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
            Text(
                ready.gutter,
                color = Funkin.Muted.copy(alpha = 0.45f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.End,
                softWrap = false,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp)
            )
            Text(
                ready.body,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                softWrap = false,
                modifier = Modifier.padding(end = 14.dp)
            )
        }
        if (total > COLLAPSED_LINES) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Funkin.Surface)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        !expanded && total > EXPANDED_LINES -> "SHOW FIRST $EXPANDED_LINES LINES"
                        !expanded -> "SHOW ALL $total LINES"
                        else -> "SHOW LESS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
fun CodeFiles(files: List<Pair<String, String>>, accent: Color) {
    if (files.isEmpty()) return
    var selected by rememberSaveable(files.map { it.first }.hashCode()) { mutableStateOf(0) }
    val index = selected.coerceIn(0, files.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (files.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                files.forEachIndexed { i, (path, _) ->
                    val active = i == index
                    val shape = RoundedCornerShape(50)
                    Text(
                        path.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Funkin.Ink else Funkin.Muted,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(shape)
                            .background(if (active) accent else Funkin.SurfaceHigh)
                            .border(1.dp, if (active) Funkin.Ink else Funkin.Outline, shape)
                            .clickable { selected = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        val (path, content) = files[index]
        CodeView(content, path, accent)
    }
}
