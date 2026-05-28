package com.pckeyboard.ime.theme

import android.graphics.Color

/**
 * Built-in themes shipped with the keyboard.
 * Following Samsung-style aesthetics with Material-You-leaning palettes.
 */
object Themes {

    val LIGHT = KeyboardTheme(
        id = "light",
        name = "Light",
        isDark = false,
        backgroundColor       = Color.parseColor("#EEF1F6"),
        keyBackgroundColor    = Color.parseColor("#FFFFFF"),
        keyPressedColor       = Color.parseColor("#D6E2F5"),
        keyTextColor          = Color.parseColor("#1A1C1E"),
        secondaryTextColor    = Color.parseColor("#5A6068"),
        modifierKeyColor      = Color.parseColor("#D9DEE6"),
        modifierTextColor     = Color.parseColor("#1A1C1E"),
        accentColor           = Color.parseColor("#1A73E8"),
        accentTextColor       = Color.parseColor("#FFFFFF"),
        dividerColor          = Color.parseColor("#C0C5CC"),
        keyCornerRadiusDp = 10,
        keyElevationDp = 1,
        keySpacingDp = 3
    )

    val DARK = KeyboardTheme(
        id = "dark",
        name = "Dark",
        isDark = true,
        backgroundColor       = Color.parseColor("#1F2125"),
        keyBackgroundColor    = Color.parseColor("#33373D"),
        keyPressedColor       = Color.parseColor("#4A5160"),
        keyTextColor          = Color.parseColor("#F2F4F7"),
        secondaryTextColor    = Color.parseColor("#B4B9C2"),
        modifierKeyColor      = Color.parseColor("#262A30"),
        modifierTextColor     = Color.parseColor("#E8EBEF"),
        accentColor           = Color.parseColor("#8AB4F8"),
        accentTextColor       = Color.parseColor("#1F2125"),
        dividerColor          = Color.parseColor("#3A3E45"),
        keyCornerRadiusDp = 10,
        keyElevationDp = 0,
        keySpacingDp = 3
    )

    val BLACK = KeyboardTheme(
        id = "black",
        name = "Black (AMOLED)",
        isDark = true,
        backgroundColor       = Color.parseColor("#000000"),
        keyBackgroundColor    = Color.parseColor("#121212"),
        keyPressedColor       = Color.parseColor("#2A2A2A"),
        keyTextColor          = Color.parseColor("#FFFFFF"),
        secondaryTextColor    = Color.parseColor("#9AA0A6"),
        modifierKeyColor      = Color.parseColor("#0A0A0A"),
        modifierTextColor     = Color.parseColor("#E8EAED"),
        accentColor           = Color.parseColor("#BB86FC"),
        accentTextColor       = Color.parseColor("#000000"),
        dividerColor          = Color.parseColor("#1F1F1F"),
        keyCornerRadiusDp = 10,
        keyElevationDp = 0,
        keySpacingDp = 3
    )

    val builtIn: List<KeyboardTheme> = listOf(LIGHT, DARK, BLACK)
}
