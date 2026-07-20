package com.pckeyboard.ime.dictionary

/**
 * Produces suggestion-strip candidates (and, when confident, an
 * auto-correct replacement) for the word currently being typed.
 *
 * Candidate sources, blended by weighted frequency rank:
 *  - accent restoration ("kerdojel" → "kérdőjel") — strongest signal,
 *    it's an exact match modulo diacritics;
 *  - edit-distance-1 corrections (deletion / transposition /
 *    substitution / insertion variants looked up in the dictionary);
 *  - prefix completions (most frequent words continuing the typed
 *    prefix) — useful while the word is still being typed, so they're
 *    weighted the most conservatively.
 *
 * The auto-replace verdict is intentionally strict — it only fires for
 * a word the dictionary doesn't know, with a clearly dominant, common
 * correction. Anything ambiguous stays a passive suggestion.
 */
class SuggestionEngine(private val dict: WordDictionary) {

    data class Result(
        /** Display candidates, best first, cased like the typed word. */
        val suggestions: List<String>,
        /** Replacement to apply on word commit in auto-correct mode, or
         *  null when nothing is confident enough. Cased like the input. */
        val autoReplace: String?
    )

    fun suggest(typed: String, maxSuggestions: Int = 3): Result {
        if (typed.length < MIN_TYPED_LENGTH) return EMPTY
        val lower = typed.lowercase()
        if (lower.any { !it.isLetter() && it != '\'' }) return EMPTY

        val typedRank = dict.rankOf(lower)
        val typedKnown = typedRank >= 0

        // word -> weighted score (lower = better).
        val scored = HashMap<String, Int>()
        fun offer(word: String, score: Int) {
            if (word == lower) return
            val prev = scored[word]
            if (prev == null || score < prev) scored[word] = score
        }

        // Accent restoration — but when the typed word is itself valid and
        // more frequent than its accented sibling ("vagyok" vs "vágyok"),
        // the user almost certainly meant what they typed; stay quiet.
        val accentRank = dict.accentRestoreRank(lower)
        if (accentRank >= 0 && (!typedKnown || accentRank < typedRank)) {
            dict.accentRestore(lower)?.let { offer(it, accentRank / ACCENT_BOOST) }
        }

        if (!typedKnown) {
            for ((word, rank) in editDistance1Hits(lower)) {
                offer(word, rank)
            }
        }

        for (completion in dict.topCompletions(lower, maxSuggestions)) {
            offer(completion, dict.rankOf(completion) * COMPLETION_PENALTY)
        }

        if (scored.isEmpty()) return EMPTY
        val ranked = scored.entries.sortedBy { it.value }.map { it.key }

        val autoReplace = pickAutoReplace(lower, typedKnown, accentRank, ranked)
        return Result(
            suggestions = ranked.take(maxSuggestions).map { matchCase(typed, it) },
            autoReplace = autoReplace?.let { matchCase(typed, it) }
        )
    }

    /** All dictionary words within edit distance 1 of [word], with their
     *  frequency ranks. A few hundred binary-search probes — cheap. */
    private fun editDistance1Hits(word: String): List<Pair<String, Int>> {
        val hits = ArrayList<Pair<String, Int>>()
        val seen = HashSet<String>()
        fun probe(candidate: String) {
            if (candidate.isEmpty() || candidate == word || !seen.add(candidate)) return
            val rank = dict.rankOf(candidate)
            if (rank >= 0) hits.add(candidate to rank)
        }
        val n = word.length
        val sb = StringBuilder(word)
        // Deletions.
        for (i in 0 until n) {
            probe(StringBuilder(word).deleteCharAt(i).toString())
        }
        // Adjacent transpositions.
        for (i in 0 until n - 1) {
            if (word[i] == word[i + 1]) continue
            sb.setLength(0); sb.append(word)
            sb.setCharAt(i, word[i + 1]); sb.setCharAt(i + 1, word[i])
            probe(sb.toString())
        }
        // Substitutions.
        for (i in 0 until n) {
            for (c in dict.alphabet) {
                if (c == word[i]) continue
                sb.setLength(0); sb.append(word)
                sb.setCharAt(i, c)
                probe(sb.toString())
            }
        }
        // Insertions.
        for (i in 0..n) {
            for (c in dict.alphabet) {
                sb.setLength(0); sb.append(word)
                sb.insert(i, c)
                probe(sb.toString())
            }
        }
        return hits
    }

    /** Conservative auto-correct verdict — see class docs. */
    private fun pickAutoReplace(
        lower: String,
        typedKnown: Boolean,
        accentRank: Int,
        ranked: List<String>
    ): String? {
        if (typedKnown) return null                    // the word is fine as typed
        if (lower.length < MIN_AUTOREPLACE_LENGTH) return null
        val best = ranked.firstOrNull() ?: return null

        // Accent restoration of a reasonably common word: always confident.
        if (accentRank in 0 until ACCENT_AUTOREPLACE_MAX_RANK &&
            dict.accentRestore(lower) == best
        ) return best

        // Edit-1 correction: only when the winner is common and clearly
        // dominates the runner-up. Completions never auto-replace.
        val bestRank = dict.rankOf(best)
        if (bestRank !in 0 until EDIT1_AUTOREPLACE_MAX_RANK) return null
        if (isEditDistanceAtMost1(lower, best).not()) return null
        val second = ranked.getOrNull(1)
        if (second != null) {
            val secondRank = dict.rankOf(second)
            if (secondRank in 0..bestRank * DOMINANCE_FACTOR &&
                isEditDistanceAtMost1(lower, second)
            ) return null                              // two plausible fixes — stay passive
        }
        return best
    }

    /** True when [b] is within one deletion/insertion/substitution/
     *  transposition of [a]. */
    private fun isEditDistanceAtMost1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length; val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        if (la == lb) {
            var diff = -1
            for (i in 0 until la) {
                if (a[i] != b[i]) {
                    if (diff >= 0) {
                        // Second mismatch — only OK as an adjacent transposition.
                        return diff == i - 1 && a[diff] == b[i] && a[i] == b[diff] &&
                            a.substring(i + 1) == b.substring(i + 1)
                    }
                    diff = i
                }
            }
            return true
        }
        val (short, long) = if (la < lb) a to b else b to a
        var i = 0; var j = 0; var skipped = false
        while (i < short.length && j < long.length) {
            if (short[i] == long[j]) { i++; j++ }
            else {
                if (skipped) return false
                skipped = true; j++
            }
        }
        return true
    }

    /** Applies the typed word's casing to a lowercase suggestion. */
    private fun matchCase(typed: String, suggestion: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } ->
            suggestion.uppercase()
        typed.first().isUpperCase() ->
            suggestion.replaceFirstChar { it.uppercase() }
        else -> suggestion
    }

    companion object {
        private val EMPTY = Result(emptyList(), null)
        /** Don't suggest before this many typed characters. */
        private const val MIN_TYPED_LENGTH = 2
        /** Never auto-replace words shorter than this. */
        private const val MIN_AUTOREPLACE_LENGTH = 3
        /** Accent restoration beats everything: effective rank is divided
         *  by this, so even a mid-frequency accented word outranks common
         *  edit-1 alternatives. */
        private const val ACCENT_BOOST = 16
        /** Completions are the least certain source: their rank is
         *  multiplied by this. */
        private const val COMPLETION_PENALTY = 2
        private const val ACCENT_AUTOREPLACE_MAX_RANK = 60_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK = 20_000
        /** Runner-up within bestRank×this counts as "competing". */
        private const val DOMINANCE_FACTOR = 4
    }
}
