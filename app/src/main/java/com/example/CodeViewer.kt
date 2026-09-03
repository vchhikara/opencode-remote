package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color

@Composable
fun CodeViewer(
    fileName: String,
    content: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val lines = content.split("\n")
    
    // Very basic syntax highlighting colors
    val keywordColor = Color(0xFFC678DD)
    val stringColor = Color(0xFF98C379)
    val commentColor = Color(0xFF5C6370)
    val numberColor = Color(0xFFD19A66)
    
    val keywords = listOf("fun", "val", "var", "if", "else", "for", "while", "return", "class", "interface", "import", "package", "public", "private", "protected", "true", "false", "null")

    Column(modifier = modifier.background(TerminalBackground, RoundedCornerShape(8.dp))) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fileName, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(content))
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Code content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val annotatedLine = buildAnnotatedString {
                    if (line.trimStart().startsWith("//")) {
                        withStyle(SpanStyle(color = commentColor)) {
                            append(line)
                        }
                    } else {
                        var i = 0
                        while (i < line.length) {
                            val c = line[i]
                            if (c == '"' || c == '\'') {
                                val start = i
                                i++
                                while (i < line.length && line[i] != c) {
                                    if (line[i] == '\\') i++ // Skip escaped char
                                    i++
                                }
                                if (i < line.length) i++
                                withStyle(SpanStyle(color = stringColor)) {
                                    append(line.substring(start, i))
                                }
                            } else if (c.isLetter()) {
                                val start = i
                                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                                    i++
                                }
                                val word = line.substring(start, i)
                                if (word in keywords) {
                                    withStyle(SpanStyle(color = keywordColor)) {
                                        append(word)
                                    }
                                } else {
                                    append(word)
                                }
                            } else if (c.isDigit()) {
                                val start = i
                                while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
                                    i++
                                }
                                withStyle(SpanStyle(color = numberColor)) {
                                    append(line.substring(start, i))
                                }
                            } else {
                                append(c.toString())
                                i++
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = (index + 1).toString(),
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (line.isEmpty()) AnnotatedString(" ") else annotatedLine,
                        color = TerminalTextDefault,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
