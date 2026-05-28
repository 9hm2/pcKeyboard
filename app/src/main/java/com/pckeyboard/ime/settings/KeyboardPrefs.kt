package com.pckeyboard.ime.settings

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * User-tunable layout sizing: height scale (so the keyboard can grow / shrink
 * vertically), horizontal padding (so it can be narrowed symmetrically from
 * both sides — useful on tablets / unfolded foldables), and a split toggle
 * which splits every row at its centre on wide screens.
 */
class KeyboardPrefs(context: Context) {

    private val prefs =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** Multiplier applied to the keyboard height, clamped to [0.5, 1.6]. */
    var heightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT, 1.0f).coerceIn(0.5f, 1.6f)
        set(value) {
            prefs.edit().putFloat(KEY_HEIGHT, value.coerceIn(0.5f, 1.6f)).apply()
        }

    /** Fraction of width to leave empty on each side, clamped to [0.0, 0.3]. */
    var horizontalPadding: Float
        get() = prefs.getFloat(KEY_HPAD, 0.0f).coerceIn(0.0f, 0.3f)
        set(value) {
            prefs.edit().putFloat(KEY_HPAD, value.coerceIn(0.0f, 0.3f)).apply()
        }

    /** When true and screen is wide, insert a centre gap in every row. */
    var splitEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLIT, false)
        set(value) { prefs.edit().putBoolean(KEY_SPLIT, value).apply() }

    /**
     * Width of the centre gap expressed as extra row weight; clamped [0.5, 6].
     * Wider → more thumb clearance.
     */
    var splitGapWeight: Float
        get() = prefs.getFloat(KEY_SPLIT_GAP, 2.0f).coerceIn(0.5f, 6.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPLIT_GAP, value.coerceIn(0.5f, 6.0f)).apply()
        }

    companion object {
        private const val KEY_HEIGHT = "kb_height_scale"
        private const val KEY_HPAD = "kb_horizontal_padding"
        private const val KEY_SPLIT = "kb_split_enabled"
        private const val KEY_SPLIT_GAP = "kb_split_gap_weight"

        /** Threshold above which split mode is applied. */
        const val SPLIT_MIN_WIDTH_DP = 600
    }
}
