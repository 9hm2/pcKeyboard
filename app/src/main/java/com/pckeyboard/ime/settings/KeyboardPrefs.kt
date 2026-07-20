package com.pckeyboard.ime.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager

/**
 * User-tunable layout sizing: height scale (so the keyboard can grow / shrink
 * vertically), horizontal padding (so it can be narrowed symmetrically from
 * both sides — useful on tablets / unfolded foldables), and a split toggle
 * which splits every row at its centre on wide screens.
 *
 * All keyboard preferences live in **device-protected storage** so they're
 * also readable on the lock screen — the IME has to be usable to actually
 * type the PIN after a reboot, and device-protected storage is the only
 * SharedPreferences area accessible in that window. The first time this
 * class is constructed it pulls existing values forward from the old
 * credential-encrypted store so users updating from earlier versions
 * keep their settings.
 */
class KeyboardPrefs(context: Context) {

    private val appContext = context.applicationContext

    private val storeContext: Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(storeContext)

    init {
        migrateFromCredentialEncryptedStorageOnce()
    }

    /** Copies every user-settings key from the legacy credential-
     *  encrypted SharedPreferences into device-protected storage so the
     *  IME has them on the lock screen too. Runs exactly once per
     *  install — guarded by a flag inside DE storage. Sensitive
     *  per-history data (clipboard, emoji usage tracker) is filtered
     *  out so those classes keep behaving as before in CE storage. */
    private fun migrateFromCredentialEncryptedStorageOnce() {
        if (storeContext === appContext) return // SDK < N — nowhere to migrate to.
        if (prefs.getBoolean(KEY_MIGRATED_V1, false)) return
        val cePrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val all = try { cePrefs.all } catch (_: Throwable) { null }
        if (all.isNullOrEmpty()) {
            prefs.edit().putBoolean(KEY_MIGRATED_V1, true).apply()
            return
        }
        val edit = prefs.edit()
        for ((key, value) in all) {
            if (!shouldCopyToDeviceStorage(key)) continue
            when (value) {
                is String  -> edit.putString(key, value)
                is Int     -> edit.putInt(key, value)
                is Long    -> edit.putLong(key, value)
                is Float   -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Set<*>  -> @Suppress("UNCHECKED_CAST")
                    edit.putStringSet(key, value as Set<String>)
            }
        }
        edit.putBoolean(KEY_MIGRATED_V1, true).apply()
    }

    private fun shouldCopyToDeviceStorage(key: String): Boolean {
        // Clipboard history + emoji usage tracker stay credential-
        // encrypted — they're personal-content stores that shouldn't be
        // readable before the user has unlocked their device.
        if (key == "clip_history_json") return false
        if (key.startsWith("emoji_n_") || key.startsWith("emoji_t_")) return false
        return true
    }

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

    /**
     * Time in milliseconds the user has to hold a key before a long-press
     * fires. Applies to every key (popup characters AND the space-trackpad).
     * Clamped to [150, 1000].
     */
    var longPressDelayMs: Int
        get() = prefs.getInt(KEY_LP_DELAY, 380).coerceIn(150, 1000)
        set(value) {
            prefs.edit().putInt(KEY_LP_DELAY, value.coerceIn(150, 1000)).apply()
        }

    /**
     * Which action the slot immediately to the right of Space performs.
     * Currently supported: "symbols" (default — flips into the 123 page),
     * "emoji" (opens the emoji picker directly), "alt" (a second sticky
     * Alt/AltGr modifier for the right thumb). The key renders the
     * matching label ("123", "😀" or "Alt") so the user sees what
     * tapping it will do. Long-pressing the key opens a chooser popup.
     */
    var rightOfSpaceAction: String
        get() = prefs.getString(KEY_RIGHT_OF_SPACE, RIGHT_OF_SPACE_SYMBOLS) ?: RIGHT_OF_SPACE_SYMBOLS
        set(value) { prefs.edit().putString(KEY_RIGHT_OF_SPACE, value).apply() }

    /**
     * Distinct from [splitEnabled] / [splitGapWeight] (which only add a
     * small centre gap on wide tablet screens). When this is on, the
     * keyboard is rendered as a true "side-split" — every row is broken
     * into a left half and a right half with a large gap in the middle,
     * Space is duplicated as two half-Space keys so both thumbs can hit
     * it, and the touchable region of the IME is restricted to the two
     * side strips so the underlying app can be tapped through the gap.
     *
     * Primary use case is landscape on a narrow phone where the regular
     * keyboard otherwise eats the whole bottom half of the screen.
     *
     * **Temporarily force-disabled** — the rendering has visual bugs we
     * haven't tracked down yet. The getter always returns `false` so
     * existing users who had the toggle on don't get stuck in the buggy
     * mode; the setter still writes the preference so when we re-enable
     * it the old state comes back.
     */
    var sideSplitEnabled: Boolean
        get() = false
        set(value) { prefs.edit().putBoolean(KEY_SIDE_SPLIT, value).apply() }

    /**
     * Force the F-key / Esc / Home / End row to be shown even on narrow
     * screens that would normally drop it to save vertical space. The
     * globe long-press menu offers a one-tap toggle for this so the user
     * can opt in to the full PC layout on a regular phone width.
     */
    var showFunctionRow: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FN_ROW, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_FN_ROW, value).apply() }

    /**
     * Multiplier applied to the space-trackpad's cursor speed (both axes).
     * 1.0 keeps the analog curve's default; values below 1.0 slow the cursor
     * down for finer placement, values above 1.0 speed it up for long jumps.
     * Clamped to [0.3, 3.0].
     */
    var trackpadSensitivity: Float
        get() = prefs.getFloat(KEY_TRACKPAD_SENS, 1.0f).coerceIn(0.3f, 3.0f)
        set(value) {
            prefs.edit().putFloat(KEY_TRACKPAD_SENS, value.coerceIn(0.3f, 3.0f)).apply()
        }

    /**
     * Autocorrect behaviour:
     *  - [AUTOCORRECT_OFF]     — no suggestion bar, no corrections.
     *  - [AUTOCORRECT_SUGGEST] — (default) passive suggestion bar above
     *                            the keys; nothing is ever changed
     *                            without a tap.
     *  - [AUTOCORRECT_AUTO]    — suggestions plus conservative automatic
     *                            replacement on word commit, undoable
     *                            with an immediate Backspace.
     */
    var autocorrectMode: String
        get() = prefs.getString(KEY_AUTOCORRECT, AUTOCORRECT_SUGGEST) ?: AUTOCORRECT_SUGGEST
        set(value) { prefs.edit().putString(KEY_AUTOCORRECT, value).apply() }

    /** The keyboard's currently selected language pack id, e.g. "en_US". */
    var currentLanguage: String
        get() = prefs.getString(KEY_LANG, "en_US") ?: "en_US"
        set(value) { prefs.edit().putString(KEY_LANG, value).apply() }

    /** Set of language pack ids the user has enabled in Settings. The globe
     *  tap cycle walks only these (plus the emoji state); at least one must
     *  remain enabled — empty inputs are coerced to {"en_US"}. */
    var enabledLanguages: Set<String>
        get() = prefs.getStringSet(KEY_ENABLED_LANGS, ALL_LANG_IDS) ?: ALL_LANG_IDS
        set(value) {
            val safe = if (value.isEmpty()) setOf("en_US") else value
            prefs.edit().putStringSet(KEY_ENABLED_LANGS, safe).apply()
        }

    /** Whether the keyboard should auto-download updates in the background
     *  while it's the default IME. The system installer still asks the user
     *  to confirm — that's an Android security guarantee, not a setting. */
    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply() }

    /** Interval in hours between auto-update checks (12 or 24). */
    var autoUpdateIntervalHours: Int
        get() = prefs.getInt(KEY_AUTO_UPDATE_HOURS, 24).coerceIn(12, 24)
        set(value) {
            val safe = if (value <= 12) 12 else 24
            prefs.edit().putInt(KEY_AUTO_UPDATE_HOURS, safe).apply()
        }

    companion object {
        val ALL_LANG_IDS: Set<String> = setOf("en_US", "hu_HU", "de_DE", "es_ES")
        private const val KEY_HEIGHT = "kb_height_scale"
        private const val KEY_HPAD = "kb_horizontal_padding"
        private const val KEY_SPLIT = "kb_split_enabled"
        private const val KEY_SPLIT_GAP = "kb_split_gap_weight"
        private const val KEY_LP_DELAY = "kb_long_press_delay_ms"
        private const val KEY_LANG = "kb_current_language"
        private const val KEY_ENABLED_LANGS = "kb_enabled_languages"
        private const val KEY_AUTO_UPDATE = "kb_auto_update_enabled"
        private const val KEY_AUTO_UPDATE_HOURS = "kb_auto_update_hours"
        private const val KEY_SHOW_FN_ROW = "kb_show_function_row"
        private const val KEY_SIDE_SPLIT = "kb_side_split_enabled"
        private const val KEY_RIGHT_OF_SPACE = "kb_right_of_space_action"
        private const val KEY_TRACKPAD_SENS = "kb_trackpad_sensitivity"
        /** Weight of the big empty centre when sideSplit is on. */
        const val SIDE_SPLIT_GAP_WEIGHT = 5f

        const val RIGHT_OF_SPACE_SYMBOLS = "symbols"
        const val RIGHT_OF_SPACE_EMOJI = "emoji"
        const val RIGHT_OF_SPACE_ALT = "alt"

        private const val KEY_AUTOCORRECT = "kb_autocorrect_mode"
        const val AUTOCORRECT_OFF = "off"
        const val AUTOCORRECT_SUGGEST = "suggest"
        const val AUTOCORRECT_AUTO = "auto"

        /** Threshold above which split mode is applied. */
        const val SPLIT_MIN_WIDTH_DP = 600

        private const val KEY_MIGRATED_V1 = "kb_migrated_to_de_v1"
    }
}
