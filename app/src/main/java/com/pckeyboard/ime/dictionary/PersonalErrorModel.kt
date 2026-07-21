package com.pckeyboard.ime.dictionary

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

/**
 * What the engine may ask of the personal error model — split into an
 * interface so tests can fake it without Android storage.
 */
interface PersonalSignals {
    /** The correction the user most often picked for [typed], with its
     *  confirmation count, or null. */
    fun correctionFor(typed: String): Pair<String, Int>?

    /** Multiplier (≤ 1) for an edit-operation penalty, based on how
     *  often the user personally makes that exact slip. Keys as in
     *  [PersonalErrorModel.extractEdits]. */
    fun editFactor(key: String): Float
}

/**
 * On-device personal typo model — no neural net, just counting what the
 * user's own fingers do. Every explicit correction (a tap on the
 * suggestion strip) is a labelled example "typed → meant":
 *
 *  - the exact pair is remembered, so a recurring personal typo
 *    ("afjin" → "adjon") corrects instantly and with high confidence;
 *  - the pair is aligned character-wise and the individual slips
 *    (substitution r→e, dropped letter, swap, …) are counted; frequent
 *    personal slips make the engine's matching edit penalties cheaper,
 *    which generalises to words never seen before.
 *
 * Insisting on a word (retype after auto-correction) erases its learned
 * pair. Stored per language in **credential-encrypted** prefs, like the
 * user dictionary — this is literally a log of the user's typing.
 */
class PersonalErrorModel(context: Context, private val langId: String) : PersonalSignals {

    private val appContext = context.applicationContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)

    /** typed(lower) -> (correction -> confirmations). */
    private val pairs: MutableMap<String, MutableMap<String, Int>> = loadPairs()
    /** edit-op key -> count (see [extractEdits]). */
    private val edits: MutableMap<String, Int> = loadEdits()

    private fun pairsKey() = "personal_typos_$langId"
    private fun editsKey() = "personal_edits_$langId"

    private fun loadPairs(): MutableMap<String, MutableMap<String, Int>> {
        return try {
            val raw = prefs.getString(pairsKey(), null) ?: return mutableMapOf()
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, MutableMap<String, Int>>()
            for (typed in obj.keys()) {
                val inner = obj.getJSONObject(typed)
                val m = mutableMapOf<String, Int>()
                for (fix in inner.keys()) m[fix] = inner.optInt(fix, 1)
                out[typed] = m
            }
            out
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    private fun loadEdits(): MutableMap<String, Int> {
        return try {
            val raw = prefs.getString(editsKey(), null) ?: return mutableMapOf()
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
            val pairsObj = JSONObject()
            for ((typed, m) in pairs) {
                val inner = JSONObject()
                for ((fix, c) in m) inner.put(fix, c)
                pairsObj.put(typed, inner)
            }
            val editsObj = JSONObject()
            for ((k, c) in edits) editsObj.put(k, c)
            prefs.edit()
                .putString(pairsKey(), pairsObj.toString())
                .putString(editsKey(), editsObj.toString())
                .apply()
        } catch (_: Throwable) {
            // Direct Boot — keep in memory only.
        }
    }

    /** The user tapped [corrected] while [typed] was in the editor. */
    fun recordCorrection(typed: String, corrected: String) {
        val t = typed.lowercase()
        val c = corrected.lowercase()
        if (t == c || t.length !in 2..32 || c.length !in 2..32) return
        val inner = pairs.getOrPut(t) { mutableMapOf() }
        inner[c] = (inner[c] ?: 0) + 1
        for (key in extractEdits(t, c)) {
            edits[key] = (edits[key] ?: 0) + 1
        }
        if (pairs.size > MAX_PAIRS) prunePairs()
        if (edits.size > MAX_EDITS) pruneEdits()
        persist()
    }

    /** The user insisted on [typed] — whatever we learned about
     *  "correcting" it is wrong. */
    fun forget(typed: String) {
        if (pairs.remove(typed.lowercase()) != null) persist()
    }

    override fun correctionFor(typed: String): Pair<String, Int>? =
        pairs[typed.lowercase()]?.maxByOrNull { it.value }?.toPair()

    override fun editFactor(key: String): Float {
        val count = edits[key] ?: return 1f
        // 2 confirmations ≈ 0.74, 5 ≈ 0.63, 15 ≈ 0.52; floored so a
        // personal habit can help but never fully erase the penalty.
        return (1.0 / (1.0 + kotlin.math.ln(1.0 + count) * 0.35))
            .toFloat().coerceAtLeast(MIN_FACTOR)
    }

    private fun prunePairs() {
        val keep = pairs.entries
            .sortedByDescending { it.value.values.max() }
            .take(MAX_PAIRS / 2)
        val kept = keep.associateTo(mutableMapOf()) { it.key to it.value }
        pairs.clear()
        pairs.putAll(kept)
    }

    private fun pruneEdits() {
        val keep = edits.entries.sortedByDescending { it.value }.take(MAX_EDITS / 2)
        val kept = keep.associateTo(mutableMapOf()) { it.key to it.value }
        edits.clear()
        edits.putAll(kept)
    }

    companion object {
        const val MAX_PAIRS = 400
        const val MAX_EDITS = 300
        const val MIN_FACTOR = 0.4f

        /**
         * Aligns a (typed, corrected) pair and returns the edit-op keys
         * it demonstrates:
         *  - "s:ab" — typed a where b was meant,
         *  - "t:ab" — typed the pair ab swapped,
         *  - "d:a"  — typed a stray a,
         *  - "i:a"  — omitted an a.
         * Complex pairs (distance > 2 or messy alignment) yield nothing
         * — the word pair itself still gets remembered by the caller.
         */
        fun extractEdits(typed: String, corrected: String): List<String> {
            val a = typed
            val b = corrected
            if (a.length == b.length) {
                val mismatches = a.indices.filter { a[it] != b[it] }
                // Adjacent transposition?
                if (mismatches.size == 2 && mismatches[1] == mismatches[0] + 1 &&
                    a[mismatches[0]] == b[mismatches[1]] &&
                    a[mismatches[1]] == b[mismatches[0]]
                ) {
                    return listOf("t:${a[mismatches[0]]}${a[mismatches[1]]}")
                }
                if (mismatches.size in 1..2) {
                    return mismatches.map { "s:${a[it]}${b[it]}" }
                }
                return emptyList()
            }
            // One char extra in typed → a stray key.
            if (a.length == b.length + 1) {
                alignSkip(a, b)?.let { return listOf("d:$it") }
            }
            // One char missing in typed → an omitted key.
            if (b.length == a.length + 1) {
                alignSkip(b, a)?.let { return listOf("i:$it") }
            }
            return emptyList()
        }

        /** If [long] equals [short] with exactly one char removed,
         *  returns that char, else null. */
        private fun alignSkip(long: String, short: String): Char? {
            var i = 0
            var j = 0
            var skipped: Char? = null
            while (i < long.length) {
                if (j < short.length && long[i] == short[j]) {
                    i++; j++
                } else {
                    if (skipped != null) return null
                    skipped = long[i]
                    i++
                }
            }
            return if (j == short.length) skipped else null
        }
    }
}
