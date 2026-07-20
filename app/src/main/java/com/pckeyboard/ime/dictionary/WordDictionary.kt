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

    companion object {
        private const val COMPLETION_SCAN_CAP = 120_000

        /** Loads `assets/dict/<langId>.dict` (gzip-compressed word list —
         *  the neutral extension matters: aapt2 transparently *decompresses
         *  and renames* assets ending in `.gz` while packaging, so a
         *  `.txt.gz` asset simply doesn't exist under that name in the
         *  APK). Returns null when the language ships no dictionary or
         *  the asset is unreadable. */
        fun load(context: Context, langId: String): WordDictionary? {
            val lines = try {
                context.assets.open("dict/$langId.dict").use { raw ->
                    GZIPInputStream(raw).bufferedReader().readLines()
                }
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
