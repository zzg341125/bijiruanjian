package io.github.latticenotes.ui

import androidx.compose.ui.graphics.Color

enum class NotePaperStyle(
    val storageValue: String,
    val displayName: String,
) {
    LIGHT("light", "浅"),
    DEFAULT("default", "默认"),
    DARK("dark", "深"),
    DARKEST("darkest", "最深"),
    ;

    companion object {
        fun fromStorage(value: String?): NotePaperStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

data class NotePaperPalette(
    val paper: Color,
    val gutter: Color,
    val rule: Color,
    val ink: Color,
    val mutedInk: Color,
)

fun notePaperPalette(style: NotePaperStyle): NotePaperPalette = when (style) {
    NotePaperStyle.LIGHT -> NotePaperPalette(
        paper = Color(0xFFFFFDF7),
        gutter = Color(0xFFF1ECE3),
        rule = Color(0xFFE4DED4),
        ink = Color(0xFF5C3C2A),
        mutedInk = Color(0xFF8A7E72),
    )
    NotePaperStyle.DEFAULT -> NotePaperPalette(
        paper = Color(0xFFFBF7ED),
        gutter = Color(0xFFF1ECE1),
        rule = Color(0xFFDED4C5),
        ink = Color(0xFF5C3C2A),
        mutedInk = Color(0xFF897966),
    )
    NotePaperStyle.DARK -> NotePaperPalette(
        paper = Color(0xFFF7EEDD),
        gutter = Color(0xFFECE1CE),
        rule = Color(0xFFD8C7AD),
        ink = Color(0xFF553A28),
        mutedInk = Color(0xFF826C55),
    )
    NotePaperStyle.DARKEST -> NotePaperPalette(
        paper = Color(0xFFF0E2CD),
        gutter = Color(0xFFE1D2BB),
        rule = Color(0xFFCDB89A),
        ink = Color(0xFF4E3525),
        mutedInk = Color(0xFF796249),
    )
}
