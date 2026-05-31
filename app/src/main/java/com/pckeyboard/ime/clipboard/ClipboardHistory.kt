package com.pckeyboard.ime.clipboard

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * Last-N clipboard texts captured by the IME's primary-clip listener.
 * Persisted as a JSON array in SharedPreferences so the cards survive
 * device reboots / process death.
 *
 * Stays in **credential-encrypted** storage (the regular default
 * SharedPreferences) — clipboard contents are user-generated personal
 * data and shouldn't be readable before the user has unlocked the
 * device. On the lock screen the read will throw because the CE store
 * isn't mounted yet; every public method swallows that and returns an
 * empty / no-op result so the IME keeps working.
 */
class ClipboardHistory(context: Context) {

    private val appContext = context.applicationContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)

    /** Newest first. */
    fun all(): List<String> {
        val raw = try { prefs.getString(KEY, null) } catch (_: Throwable) { null }
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Throwable) { emptyList() }
    }

    fun add(text: String) {
        if (text.isBlank()) return
        val list = all().toMutableList()
        list.remove(text)
        list.add(0, text)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(list)
    }

    fun remove(text: String) {
        val filtered = all().filter { it != text }
        save(filtered)
    }

    fun clear() {
        save(emptyList())
    }

    fun replace(old: String, new: String) {
        if (new.isBlank()) { remove(old); return }
        val list = all().toMutableList()
        val idx = list.indexOf(old)
        if (idx < 0) { add(new); return }
        list[idx] = new
        // Dedupe in case the new value collides with another entry.
        val seen = mutableSetOf<String>()
        save(list.filter { seen.add(it) })
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        try {
            prefs.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) {
            // CE storage not yet available (Direct Boot) — silently drop.
        }
    }

    companion object {
        private const val KEY = "clip_history_json"
        const val MAX = 20
    }
}
