package com.example.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object AnsiParser {
    private val ansiRegex = Regex("\u001B\\[([0-9;]*)m")

    fun parse(text: String, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            val matches = ansiRegex.findAll(text)

            var currentColor = defaultColor
            var currentWeight = FontWeight.Normal

            for (match in matches) {
                if (match.range.first > currentIndex) {
                    val subText = text.substring(currentIndex, match.range.first)
                    withStyle(SpanStyle(color = currentColor, fontWeight = currentWeight)) {
                        append(subText)
                    }
                }

                val codeStr = match.groups[1]?.value ?: ""
                val codes = codeStr.split(";").mapNotNull { it.toIntOrNull() }

                if (codes.isEmpty() || codes.contains(0)) {
                    currentColor = defaultColor
                    currentWeight = FontWeight.Normal
                } else {
                    for (code in codes) {
                        when (code) {
                            1 -> currentWeight = FontWeight.Bold
                            30 -> currentColor = Color.Black
                            31 -> currentColor = Color.Red
                            32 -> currentColor = Color.Green
                            33 -> currentColor = Color.Yellow
                            34 -> currentColor = Color.Blue
                            35 -> currentColor = Color.Magenta
                            36 -> currentColor = Color.Cyan
                            37 -> currentColor = Color.White
                            90 -> currentColor = Color.DarkGray
                            91 -> currentColor = Color.Red
                            92 -> currentColor = Color.Green
                            93 -> currentColor = Color.Yellow
                            94 -> currentColor = Color.Blue
                            95 -> currentColor = Color.Magenta
                            96 -> currentColor = Color.Cyan
                            97 -> currentColor = Color.White
                        }
                    }
                }
                currentIndex = match.range.last + 1
            }

            if (currentIndex < text.length) {
                withStyle(SpanStyle(color = currentColor, fontWeight = currentWeight)) {
                    append(text.substring(currentIndex))
                }
            }
        }
    }
}
