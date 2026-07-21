package com.pckeyboard.ime.dictionary

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * In-memory word-frequency dictionary for one language.
 *
 * Backed by the `assets/dict/<langId>.txt.gz` files produced by
 * `scripts/generate_dictionaries.py`: one lowercase word per line,
 * ordered by descending corpus frequency, so a word's line number IS
 * its frequency rank (0 = most frequent).
 *
 * Lookup structures:
 *  - [words]      — rank order, used for rank→word and frequency-ordered
 *                   prefix-completion scans.
 *  - [sortedIdx]  — indices into [words] in lexicographic order, used for
 *                   O(log n) exact lookups (the suggestion engine probes
 *                   hundreds of edit-distance variants per keypress).
 *  - [accentToRank] — deaccented form → rank of the most frequent
 *                   accented word with that skeleton ("kerdojel" →
 *                   rank of "kérdőjel"). Powers accent restoration,
 *                   which is the highest-value correction for Hungarian.
 */
class WordDictionary private constructor(
    /** Language pack id this dictionary belongs to ("hu_HU", …) — the
     *  suggestion engine derives the physical key-adjacency map from it. */
    val langId: String,
    private val words: Array<String>,
    /** Deaccented skeleton of each word, parallel to [words]; shares the
     *  same String instance when the word has no accents. Lets prefix
     *  completion match accent-insensitively without re-deaccenting the
     *  whole list on every keypress. */
    private val skeletons: Array<String>,
    private val sortedIdx: IntArray,
    private val accentToRank: HashMap<String, Int>,
    /** Distinct characters appearing in the word list — the alphabet the
     *  suggestion engine uses to generate substitution/insertion variants. */
    val alphabet: CharArray
) {

    val size: Int get() = words.size

    fun wordAt(rank: Int): String = words[rank]

    /** Precomputed deaccented skeleton of [wordAt] — same instance when
     *  the word carries no accents. Lets the edit-distance-2 scan avoid
     *  re-deaccenting tens of thousands of words per keypress. */
    fun skeletonAt(rank: Int): String = skeletons[rank]

    /** Frequency rank of [word] (0 = most frequent), or -1 if absent.
     *  Expects lowercase input. */
    fun rankOf(word: String): Int {
        var lo = 0
        var hi = sortedIdx.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = words[sortedIdx[mid]].compareTo(word)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return sortedIdx[mid]
            }
        }
        return -1
    }

    fun contains(word: String): Boolean = rankOf(word) >= 0

    /** The most frequent accented word whose deaccented skeleton matches
     *  [word]'s, or null. Returns null when the match IS [word] itself. */
    fun accentRestore(word: String): String? {
        val rank = accentToRank[deaccent(word)] ?: return null
        val restored = words[rank]
        return if (restored == word) null else restored
    }

    /** Rank of the accent-restored form of [word], or -1. */
    fun accentRestoreRank(word: String): Int =
        accentToRank[deaccent(word)] ?: -1

    /**
     * Up to [n] most frequent words strictly longer than [prefix] that
     * start with it, compared **accent-insensitively** — typing "kerd"
     * (or "kérd") surfaces "kérdés", "kérdezni", … . Scans the
     * rank-ordered list so results come out in frequency order; the scan
     * is capped at [maxRank] because a completion so rare it ranks below
     * that isn't worth suggesting.
     */
    fun topCompletions(prefix: String, n: Int, maxRank: Int = COMPLETION_SCAN_CAP): List<String> {
        if (prefix.isEmpty() || n <= 0) return emptyList()
        val skeletonPrefix = deaccent(prefix)
        val out = ArrayList<String>(n)
        val limit = minOf(maxRank, words.size)
        for (rank in 0 until limit) {
            if (skeletons[rank].length > skeletonPrefix.length &&
                skeletons[rank].startsWith(skeletonPrefix)
            ) {
                out.add(words[rank])
                if (out.size == n) break
            }
        }
        return out
    }

    /**
     * Typo-tolerant completions: words continuing a prefix that is
     * within ONE slip of what was typed (one substitution, one adjacent
     * swap, one extra or one missing character — first letter
     * included). This is what keeps the strip alive when the typo is in
     * the first letters of a word still being typed ("havít…" →
     * "javítani"): exact-prefix completion finds nothing there.
     */
    fun fuzzyCompletions(prefix: String, n: Int, maxRank: Int = FUZZY_SCAN_CAP): List<String> {
        if (prefix.length < 3 || n <= 0) return emptyList()
        val p = deaccent(prefix)
        val out = ArrayList<String>(n)
        val limit = minOf(maxRank, words.size)
        for (rank in 0 until limit) {
            val sk = skeletons[rank]
            if (sk.length <= p.length) continue
            if (prefixWithinOneSlip(p, sk)) {
                out.add(words[rank])
                if (out.size == n) break
            }
        }
        return out
    }

    /** True when the first |p| region of [sk] equals [p] up to one slip
     *  (substitution, adjacent swap, one inserted or one skipped char). */
    private fun prefixWithinOneSlip(p: String, sk: String): Boolean {
        // Same-length window: at most one substitution or one adjacent swap.
        var mismatches = 0
        var i = 0
        while (i < p.length) {
            if (p[i] != sk[i]) {
                if (mismatches == 0 && i + 1 < p.length &&
                    p[i] == sk[i + 1] && p[i + 1] == sk[i]
                ) {
                    mismatches++          // adjacent swap consumes both positions
                    i += 2
                    continue
                }
                mismatches++
                if (mismatches > 1) break
            }
            i++
        }
        if (mismatches <= 1) return true
        // One char typed extra: dropping one char of p leaves a prefix of sk.
        if (dropOneMatches(p, sk)) return true
        // One char omitted: p matches sk's slightly longer prefix minus one char.
        return dropOneMatches(sk.substring(0, minOf(sk.length, p.length + 1)), p)
    }

    /** Walk [longer] against [other] allowing exactly one skipped char in
     *  [longer]; true when [longer] is consumed (or [other] runs out). */
    private fun dropOneMatches(longer: String, other: CharSequence): Boolean {
        var i = 0
        var j = 0
        var skipped = false
        while (i < longer.length && j < other.length) {
            if (longer[i] == other[j]) { i++; j++ }
            else {
                if (skipped) return false
                skipped = true; i++
            }
        }
        return true
    }

    companion object {
        private const val COMPLETION_SCAN_CAP = 120_000
        private const val FUZZY_SCAN_CAP = 60_000

        /** Loads `assets/dict/<langId>.dict` (gzip-compressed word list —
         *  the neutral extension matters: aapt2 transparently *decompresses
         *  and renames* assets ending in `.gz` while packaging, so a
         *  `.txt.gz` asset simply doesn't exist under that name in the
         *  APK). Returns null when the language ships no dictionary or
         *  the asset is unreadable. */
        fun load(context: Context, langId: String): WordDictionary? {
            return try {
                context.assets.open("dict/$langId.dict").use { raw ->
                    fromGzipStream(langId, raw)
                }
            } catch (_: Throwable) {
                null
            }
        }

        /** Builds the dictionary from a gzip word-list stream — split out
         *  of [load] so host-JVM tests can feed the real asset files
         *  without an Android Context. */
        fun fromGzipStream(langId: String, raw: java.io.InputStream): WordDictionary? {
            val lines = try {
                GZIPInputStream(raw).bufferedReader().readLines()
            } catch (_: Throwable) {
                return null
            }
            if (lines.isEmpty()) return null
            val words = lines.toTypedArray()

            val sortedIdx = (words.indices).sortedBy { words[it] }.toIntArray()

            val accentToRank = HashMap<String, Int>()
            val chars = sortedSetOf<Char>()
            val skeletons = Array(words.size) { rank ->
                val w = words[rank]
                for (c in w) chars.add(c)
                val skeleton = deaccent(w)
                if (skeleton !== w) {
                    // First hit wins — rank order means it's the most frequent.
                    accentToRank.putIfAbsent(skeleton, rank)
                }
                skeleton
            }
            return WordDictionary(langId, words, skeletons, sortedIdx, accentToRank, chars.toCharArray())
        }

        /** Strips the accents/diacritics used by the shipped languages.
         *  ß is left alone (mapping it to "ss" would change word length,
         *  which the variant generator doesn't expect). */
        fun deaccent(s: String): String {
            var changed = false
            val out = CharArray(s.length)
            for (i in s.indices) {
                val d = when (s[i]) {
                    'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'
                    'ó', 'ö', 'ő' -> 'o'
                    'ú', 'ü', 'ű' -> 'u'
                    'ä' -> 'a'
                    'ñ' -> 'n'
                    else -> s[i]
                }
                if (d != s[i]) changed = true
                out[i] = d
            }
            return if (changed) String(out) else s
        }
    }
}
