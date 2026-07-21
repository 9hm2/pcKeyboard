package com.pckeyboard.ime.dictionary

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

/** Engine-facing view of the personal word-pair memory. */
interface PersonalBigramSignals {
    /** How many times the user has typed `prev -> next`. */
    fun count(prev: String, next: String): Int
}

/**
 * The user's own word-pair habits, learned locally from committed text
 * — the personalisation layer Samsung/Gboard predictions have. The
 * corpus bigrams know how *people* write; this knows how *you* write:
 * names you pair, phrases you repeat. Boosts context scoring (stronger
 * than corpus counts) and feeds next-word prediction first.
 *
 * Stored per language in **credential-encrypted** prefs like every
 * other personal store; capped and pruned by least-used.
 */
class PersonalBigrams(context: Context, private val langId: String) : PersonalBigramSignals {

    private val appContext = context.applicationContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)

    /** "prev next" -> count. */
    private val counts: MutableMap<String, Int> = load()

    private fun key() = "personal_bigrams_$langId"

    private fun load(): MutableMap<String, Int> {
        return try {
            val raw = prefs.getString(key(), null) ?: return mutableMapOf()
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, Int>()
            for (k in obj.keys()) out[k] = obj.optInt(k, 1)
            out
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    private fun persist() {
        try {
            val obj = JSONObject()
            for ((k, c) in counts) obj.put(k, c)
            prefs.edit().putString(key(), obj.toString()).apply()
        } catch (_: Throwable) {
            // Direct Boot — keep in memory only.
        }
    }

    /** Records one committed `prev -> next` pair. */
    fun record(prev: String, next: String) {
        val p = prev.lowercase()
        val n = next.lowercase()
        if (p.length !in 1..32 || n.length !in 1..32) return
        val k = "$p $n"
        counts[k] = (counts[k] ?: 0) + 1
        if (counts.size > MAX_PAIRS) prune()
        persist()
    }

    override fun count(prev: String, next: String): Int =
        counts["${prev.lowercase()} ${next.lowercase()}"] ?: 0

    /** The user's own most frequent continuations of [prev], filtered to
     *  pairs seen at least [MIN_PREDICT_COUNT] times. */
    fun topNext(prev: String, n: Int): List<String> {
        val prefix = "${prev.lowercase()} "
        return counts.entries.asSequence()
            .filter { it.key.startsWith(prefix) && it.value >= MIN_PREDICT_COUNT }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key.removePrefix(prefix) }
            .toList()
    }

    private fun prune() {
        val keep = counts.entries.sortedByDescending { it.value }.take(MAX_PAIRS / 2)
        val kept = keep.associateTo(mutableMapOf()) { it.key to it.value }
        counts.clear()
        counts.putAll(kept)
    }

    companion object {
        const val MAX_PAIRS = 4000
        /** A pair must repeat before it's trusted for prediction —
         *  one-off sentences shouldn't echo back. */
        const val MIN_PREDICT_COUNT = 2
    }
}
