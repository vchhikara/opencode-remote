package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalTextDefault

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val parts = text.split("```")
    Column(modifier = modifier) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Code block
                val firstNewLine = part.indexOf('\n')
                val code = if (firstNewLine != -1) part.substring(firstNewLine + 1).trimEnd() else part.trim()
                val lang = if (firstNewLine != -1) part.substring(0, firstNewLine).trim() else ""
                
                CodeViewer(
                    fileName = if (lang.isNotEmpty()) lang else "code",
                    content = code,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                // Text block
                if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
