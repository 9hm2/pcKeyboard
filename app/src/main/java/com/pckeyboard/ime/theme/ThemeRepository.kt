package com.pckeyboard.ime.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.pckeyboard.ime.util.directBootSafeContext

/**
 * Persists the selected theme and any user-built custom themes.
 *
 * Storage layout:
 *  - "selected_theme_id"   : id of the active theme
 *  - "custom_theme_ids"    : comma-separated list of saved custom theme ids
 *  - "theme_<id>_<field>"  : individual field of a custom theme
 */
class ThemeRepository(context: Context) {

    private val appContext = context.applicationContext

    // Recomputed on every access so once the user unlocks after a
    // reboot we swap from the (empty) device-protected store back to
    // the real credential-encrypted one and the saved theme reappears.
    // See KeyboardPrefs for the same pattern.
    private val prefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            appContext.directBootSafeContext()
        )

    fun getSelectedTheme(): KeyboardTheme {
        val id = prefs.getString(KEY_SELECTED, Themes.LIGHT.id) ?: Themes.LIGHT.id
        return getThemeById(id) ?: Themes.LIGHT
    }

    fun selectTheme(id: String) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun getThemeById(id: String): KeyboardTheme? {
        val builtIn = Themes.builtIn.firstOrNull { it.id == id }
        if (builtIn != null) return builtIn
        return loadCustom(id)
    }

    fun listCustomThemes(): List<KeyboardTheme> =
        readCustomIds().mapNotNull { loadCustom(it) }

    fun saveCustomTheme(theme: KeyboardTheme) {
        val ids = readCustomIds().toMutableSet()
        ids.add(theme.id)
        prefs.edit()
            .putStringSet(KEY_CUSTOM_IDS, ids)
            .apply()
        val editor = prefs.edit()
        theme.toMap().forEach { (k, v) ->
            editor.putString("$KEY_PREFIX${theme.id}_$k", v)
        }
        editor.apply()
    }

    fun deleteCustomTheme(id: String) {
        val ids = readCustomIds().toMutableSet()
        if (!ids.remove(id)) return
        val editor = prefs.edit().putStringSet(KEY_CUSTOM_IDS, ids)
        for (k in prefs.all.keys.filter { it.startsWith("$KEY_PREFIX${id}_") }) {
            editor.remove(k)
        }
        editor.apply()
        if (prefs.getString(KEY_SELECTED, "") == id) selectTheme(Themes.LIGHT.id)
    }

    fun allThemes(): List<KeyboardTheme> = Themes.builtIn + listCustomThemes()

    private fun loadCustom(id: String): KeyboardTheme? {
        val map = mutableMapOf<String, String>()
        for (k in prefs.all.keys.filter { it.startsWith("$KEY_PREFIX${id}_") }) {
            val field = k.removePrefix("$KEY_PREFIX${id}_")
            val value = prefs.getString(k, null) ?: continue
            map[field] = value
        }
        if (map.isEmpty()) return null
        if (!map.containsKey("id")) map["id"] = id
        return KeyboardTheme.fromMap(map)
    }

    private fun readCustomIds(): Set<String> =
        prefs.getStringSet(KEY_CUSTOM_IDS, emptySet()) ?: emptySet()

    companion object {
        private const val KEY_SELECTED = "selected_theme_id"
        private const val KEY_CUSTOM_IDS = "custom_theme_ids"
        private const val KEY_PREFIX = "theme_"
    }
}
