package com.pckeyboard.ime.theme

import android.os.Bundle
import androidx.core.graphics.ColorUtils

/**
 * Bridges a host terminal app's colours into a keyboard theme.
 *
 * A co-operating terminal (NeoTerm) advertises its live background and
 * foreground colours through `EditorInfo.extras` when it opens the keyboard.
 * We turn those two colours into a complete [KeyboardTheme] so the keyboard
 * blends into the terminal sitting above it.
 *
 * Only the background and foreground are received. Every other shade —
 * including the accent — is *derived* from those two colours, so the result
 * deviates only minimally from the terminal's own palette instead of dropping
 * a foreign highlight hue onto it.
 *
 * The extras keys are namespaced under this app's package and match the
 * constants written by NeoTerm's `TerminalView`.
 */
object TerminalThemeBridge {

    /** `EditorInfo.extras` key carrying the terminal background colour (ARGB int). */
    const val EXTRA_BACKGROUND = "com.pckeyboard.ime.theme.BACKGROUND"

    /** `EditorInfo.extras` key carrying the terminal foreground colour (ARGB int). */
    const val EXTRA_FOREGROUND = "com.pckeyboard.ime.theme.FOREGROUND"

    /**
     * Builds a theme from the given extras, or `null` when the host did not
     * advertise terminal colours (i.e. this is not a NeoTerm session).
     *
     * Only colours are taken from the terminal; the keyboard's own layout
     * geometry (corner radius, spacing, elevation) is preserved from [base]
     * — the user's currently selected theme — so entering NeoTerm recolours
     * the keyboard without reshaping it.
     */
    fun fromExtras(extras: Bundle?, base: KeyboardTheme): KeyboardTheme? {
        if (extras == null) return null
        if (!extras.containsKey(EXTRA_BACKGROUND) || !extras.containsKey(EXTRA_FOREGROUND)) return null
        val background = opaque(extras.getInt(EXTRA_BACKGROUND))
        val foreground = opaque(extras.getInt(EXTRA_FOREGROUND))
        return derive(background, foreground, base)
    }

    /**
     * Derives a full keyboard theme from a background + foreground pair.
     *
     * The keyboard surface is the terminal background and key text is the
     * terminal foreground. Every other shade is a small blend between the two
     * so the keyboard stays visually anchored to the terminal; nothing is
     * pulled from outside the supplied pair. The layout geometry — corner
     * radius, key spacing and elevation — is carried over from [base] rather
     * than redefined, so only the colours change in NeoTerm.
     */
    fun derive(background: Int, foreground: Int, base: KeyboardTheme): KeyboardTheme {
        val dark = ColorUtils.calculateLuminance(background) < 0.5

        // Keys sit almost flush with the background — only a hair towards the
        // foreground so an edge is visible without the keys reading as a
        // different colour from the terminal.
        val keyBg = blend(background, foreground, 0.035f)
        val keyPressed = blend(background, foreground, 0.11f)
        val modifierBg = blend(background, foreground, 0.015f)
        val divider = blend(background, foreground, 0.06f)
        // Hint text dimmed towards the background.
        val secondary = blend(foreground, background, 0.40f)

        // Accent derived purely from the terminal's own colours: a very small
        // nudge of the background towards the foreground. Special keys (space /
        // enter / backspace, locked modifiers) stay almost flush with the
        // terminal background — just distinguishable, not a foreign highlight.
        val accent = blend(background, foreground, 0.09f)
        // Pick whichever source colour stays legible on top of the accent.
        val accentText =
            if (contrast(accent, foreground) >= contrast(accent, background)) foreground else background

        return KeyboardTheme(
            id = "neoterm",
            name = "Terminal",
            isDark = dark,
            backgroundColor = background,
            keyBackgroundColor = keyBg,
            keyPressedColor = keyPressed,
            keyTextColor = foreground,
            secondaryTextColor = secondary,
            modifierKeyColor = modifierBg,
            modifierTextColor = foreground,
            accentColor = accent,
            accentTextColor = accentText,
            dividerColor = divider,
            // Keep the user's own layout geometry — only the colours come
            // from the terminal.
            keyCornerRadiusDp = base.keyCornerRadiusDp,
            keyElevationDp = base.keyElevationDp,
            keySpacingDp = base.keySpacingDp
        )
    }

    private fun opaque(color: Int): Int = color or (0xFF shl 24)

    private fun blend(from: Int, to: Int, ratio: Float): Int =
        ColorUtils.blendARGB(from, to, ratio)

    private fun contrast(a: Int, b: Int): Double =
        ColorUtils.calculateContrast(opaque(a), opaque(b))
}
