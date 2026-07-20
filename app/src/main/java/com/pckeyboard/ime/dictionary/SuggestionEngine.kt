package com.pckeyboard.ime.dictionary

/**
 * Produces suggestion-strip candidates (and, when confident, an
 * auto-correct replacement) for the word currently being typed.
 *
 * Candidate sources, blended by weighted frequency rank:
 *  - accent restoration ("kerdojel" → "kérdőjel") — strongest signal,
 *    it's an exact match modulo diacritics;
 *  - edit-distance-1 corrections, where each edit is weighted by how
 *    plausible that slip actually is: adjacent-key substitutions and
 *    swapped/missing/extra letters are cheap, a substitution with a key
 *    from the other side of the keyboard is expensive;
 *  - prefix completions (most frequent words continuing the typed
 *    prefix, accent-insensitively) — weighted the most conservatively;
 *  - the user's own learned words, ranked by personal use count.
 *
 * The auto-replace verdict only fires for a word no dictionary knows,
 * with a clearly dominant correction — but "dominant" is judged on the
 * weighted scores, so a likely slip beats an unlikely one instead of
 * both blocking each other.
 */
class SuggestionEngine(
    private val dict: WordDictionary,
    /** The user's own learned words — they count as valid (blocking
     *  auto-correction), surface as completions ranked by how often the
     *  user types them, and are reachable through typo correction. */
    private val user: UserDictionary? = null,
    /** Morphological word validator (Hunspell) — knows every inflection
     *  and compound from stems + affix rules, far beyond what any corpus
     *  list can cover. A validated typed word is never auto-corrected,
     *  and deep-tail candidates must pass it to be offered. Null while
     *  the checker is still loading — everything then behaves as before. */
    private val validator: ((String) -> Boolean)? = null
) {

    data class Result(
        /** Display candidates, best first, cased like the typed word. */
        val suggestions: List<String>,
        /** Replacement to apply on word commit in auto-correct mode, or
         *  null when nothing is confident enough. Cased like the input. */
        val autoReplace: String?
    )

    private val adjacency: Map<Char, String> = adjacencyFor(dict.langId)

    fun suggest(typed: String, maxSuggestions: Int = 3): Result {
        if (typed.length < MIN_TYPED_LENGTH) return EMPTY
        val lower = typed.lowercase()
        if (lower.any { !it.isLetter() && it != '\'' }) return EMPTY

        val typedRank = dict.rankOf(lower)
        // "Trusted" = the user's word stands: a genuinely common corpus
        // word, one the user personally taught us, or one Hunspell
        // validates morphologically (rare inflections, compounds — the
        // corpus list can never cover those). Deep-tail corpus entries
        // (rank ≥ WEAK_TYPED_RANK) are NOT trusted by rank alone — the
        // subtitle corpus's tail is full of typos and accentless
        // spellings ("hpgy", "talalkozunk"), and treating those as valid
        // words was blocking exactly the corrections users want most.
        val typedValidWord = validator?.let { v ->
            v(typed) || (typed != lower && v(lower))
        } == true
        val typedTrusted = typedValidWord ||
            (typedRank in 0 until WEAK_TYPED_RANK) ||
            user?.isKnown(lower) == true

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
        if (accentRank >= 0 && (typedRank < 0 || accentRank < typedRank)) {
            dict.accentRestore(lower)?.let { offer(it, accentRank / ACCENT_BOOST) }
        }

        if (!typedTrusted && lower.length >= MIN_EDIT1_LENGTH) {
            for ((word, score) in editDistance1Hits(lower)) {
                offer(word, score)
            }
        }

        for (completion in dict.topCompletions(lower, maxSuggestions)) {
            offer(completion, dict.rankOf(completion) * COMPLETION_PENALTY)
        }

        // The user's own words complete too — scored by personal use
        // count, so a name typed daily quickly outranks corpus words.
        user?.knownCompletions(lower, maxSuggestions)?.forEach { (word, count) ->
            offer(word, userScore(count))
        }

        if (scored.isEmpty()) return EMPTY
        // Deep-tail candidates must pass the morphological validator (when
        // available) — this keeps corpus junk out of the strip AND out of
        // auto-correction. Bounded to the best few dozen entries so a
        // pathological word can't trigger hundreds of spell() calls.
        val ranked = scored.entries.sortedBy { it.value }
            .take(CANDIDATE_VET_LIMIT)
            .filter { candidateAcceptable(it.key) }

        if (ranked.isEmpty()) return EMPTY
        val autoReplace = pickAutoReplace(lower, typedRank, typedTrusted, accentRank, ranked)
        return Result(
            suggestions = ranked.take(maxSuggestions).map { matchCase(typed, it.key) },
            autoReplace = autoReplace?.let { matchCase(typed, it) }
        )
    }

    /** Whether a candidate may be shown / used as a correction: the
     *  user's own words always, common corpus words by rank, everything
     *  else only if the validator (when loaded) accepts it — lowercase
     *  or capitalised (proper nouns are stored capitalised in Hunspell). */
    private fun candidateAcceptable(word: String): Boolean {
        if ((user?.knownCount(word) ?: 0) > 0) return true
        val rank = dict.rankOf(word)
        if (rank in 0 until CANDIDATE_TRUST_RANK) return true
        val v = validator ?: return true
        return v(word) || v(word.replaceFirstChar { it.uppercase() })
    }

    /**
     * All dictionary / user words within edit distance 1 of [word], with
     * weighted scores (frequency rank × slip-plausibility penalty).
     * Each variant is looked up literally AND through the accent map, so
     * a typo combined with missing accents still lands:
     * "bilentyuzet" --insert l--> "billentyuzet" --map--> "billentyűzet".
     */
    private fun editDistance1Hits(word: String): List<Pair<String, Int>> {
        val hits = ArrayList<Pair<String, Int>>()
        val seen = HashSet<String>()
        fun probe(candidate: String, penalty: Float) {
            if (candidate.isEmpty() || candidate == word || !seen.add(candidate)) return
            val rank = dict.rankOf(candidate)
            if (rank >= 0) hits.add(candidate to (rank * penalty).toInt())
            val accentRank = dict.accentRestoreRank(candidate)
            if (accentRank >= 0) {
                hits.add(dict.wordAt(accentRank) to
                    (accentRank * penalty * ACCENT_VARIANT_PENALTY).toInt())
            }
            val userCount = user?.knownCount(candidate) ?: 0
            if (userCount > 0) hits.add(candidate to (userScore(userCount) * penalty).toInt())
        }
        val n = word.length
        val sb = StringBuilder(word)
        // Probe in ascending-penalty order so when two edit paths produce
        // the same string, the cheaper interpretation wins the seen-set.
        // Adjacent transpositions — the classic fast-typing slip.
        for (i in 0 until n - 1) {
            if (word[i] == word[i + 1]) continue
            sb.setLength(0); sb.append(word)
            sb.setCharAt(i, word[i + 1]); sb.setCharAt(i + 1, word[i])
            probe(sb.toString(), PENALTY_TRANSPOSE)
        }
        // Deletions (an extra character slipped in).
        for (i in 0 until n) {
            probe(StringBuilder(word).deleteCharAt(i).toString(), PENALTY_DELETE)
        }
        // Insertions (a character was skipped).
        for (i in 0..n) {
            for (c in dict.alphabet) {
                sb.setLength(0); sb.append(word)
                sb.insert(i, c)
                probe(sb.toString(), PENALTY_INSERT)
            }
        }
        // Substitutions — cheap for neighbouring keys / accent siblings,
        // expensive for keys from the other side of the keyboard.
        for (i in 0 until n) {
            for (c in dict.alphabet) {
                if (c == word[i]) continue
                sb.setLength(0); sb.append(word)
                sb.setCharAt(i, c)
                val penalty = if (isNearMiss(word[i], c)) PENALTY_SUB_NEAR else PENALTY_SUB_FAR
                probe(sb.toString(), penalty)
            }
        }
        return hits
    }

    /** True when mistyping [a] as [b] is physically plausible: the keys
     *  neighbour each other on this language's layout, or the two chars
     *  are accent siblings (a ↔ á). */
    private fun isNearMiss(a: Char, b: Char): Boolean {
        if (WordDictionary.deaccent(a.toString()) == WordDictionary.deaccent(b.toString())) return true
        return adjacency[a]?.contains(b) == true
    }

    /** Conservative auto-correct verdict — see class docs. */
    private fun pickAutoReplace(
        lower: String,
        typedRank: Int,
        typedTrusted: Boolean,
        accentRank: Int,
        ranked: List<Map.Entry<String, Int>>
    ): String? {
        if (typedTrusted) return null                  // the word is fine as typed
        if (lower.length < MIN_AUTOREPLACE_LENGTH) return null
        val best = ranked.firstOrNull() ?: return null
        // Typed word sits in the corpus's deep tail — correctable, but
        // only by something MUCH more common than itself.
        val weakKnown = typedRank >= 0

        // Accent restoration of a reasonably common word: always confident.
        // Longer words get a deeper cap — their skeleton match is near-unique.
        val accentCap = if (lower.length >= 5) ACCENT_AUTOREPLACE_MAX_RANK_LONG
                        else ACCENT_AUTOREPLACE_MAX_RANK_SHORT
        if (accentRank in 0 until accentCap && dict.accentRestore(lower) == best.key &&
            (!weakKnown || typedRank >= accentRank * WEAK_ACCENT_FACTOR)
        ) {
            return best.key
        }

        // Edit-1 correction: the winner must be common (cap scales with
        // word length — long words have few edit-1 neighbours, so deeper
        // corrections are safe) and clearly dominate the runner-up on
        // weighted score. Distances are measured on deaccented skeletons
        // so "one typo + missing accents" still counts as a single edit.
        val bestRank = dict.rankOf(best.key)
        val rankCap = when {
            lower.length >= 6 -> EDIT1_AUTOREPLACE_MAX_RANK_LONG
            lower.length == 5 -> EDIT1_AUTOREPLACE_MAX_RANK_MID
            else -> EDIT1_AUTOREPLACE_MAX_RANK_SHORT
        }
        val bestIsUserWord = (user?.knownCount(best.key) ?: 0) > 0
        if (bestRank !in 0 until rankCap && !bestIsUserWord) return null
        val skeleton = WordDictionary.deaccent(lower)
        val bestSkeleton = WordDictionary.deaccent(best.key)
        if (!isEditDistanceAtMost1(skeleton, bestSkeleton)) return null
        if (weakKnown) {
            // Accent-only differences must go through the accent branch
            // above (protects deliberate accents: "vágyok" never becomes
            // "vagyok"), and a real typo fix must be far more common
            // than the deep-tail "word" it replaces.
            if (skeleton == bestSkeleton) return null
            if (bestRank < 0 || bestRank * WEAK_EDIT_FACTOR > typedRank) return null
        }
        val second = ranked.getOrNull(1)
        if (second != null &&
            second.value <= maxOf(best.value, SCORE_FLOOR) * DOMINANCE_FACTOR &&
            isEditDistanceAtMost1(skeleton, WordDictionary.deaccent(second.key))
        ) return null                                  // two equally plausible fixes — stay passive
        return best.key
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

    /** Effective "rank" of a user word with the given use count — the
     *  more the user types it, the closer it gets to the top. */
    private fun userScore(count: Int): Int = USER_RANK_BASE / count

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
        /** Don't suggest before this many typed characters — completions
         *  and accent restoration kick in from the very first letter. */
        private const val MIN_TYPED_LENGTH = 1
        /** Edit-1 variants of a 1-char fragment are pure noise (every
         *  short word qualifies) — only run typo correction from here. */
        private const val MIN_EDIT1_LENGTH = 2
        /** Never auto-replace words shorter than this. */
        private const val MIN_AUTOREPLACE_LENGTH = 3
        /** Accent restoration beats everything: effective rank is divided
         *  by this, so even a mid-frequency accented word outranks common
         *  edit-1 alternatives. */
        private const val ACCENT_BOOST = 16
        /** Completions are the least certain source: their rank is
         *  multiplied by this. */
        private const val COMPLETION_PENALTY = 2
        /** Reaching a word through an edit AND the accent map is a
         *  little less certain than either alone. */
        private const val ACCENT_VARIANT_PENALTY = 1.2f

        // Slip plausibility. Multiplied onto the frequency rank, so 2.5
        // means "as convincing as a 2.5× rarer word typed cleanly".
        private const val PENALTY_TRANSPOSE = 0.9f
        private const val PENALTY_DELETE = 1.0f
        private const val PENALTY_INSERT = 1.0f
        private const val PENALTY_SUB_NEAR = 1.0f
        private const val PENALTY_SUB_FAR = 2.5f

        private const val ACCENT_AUTOREPLACE_MAX_RANK_SHORT = 60_000
        private const val ACCENT_AUTOREPLACE_MAX_RANK_LONG = 250_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_SHORT = 30_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_MID = 80_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_LONG = 150_000
        /** Runner-up within bestScore×this counts as "competing". */
        private const val DOMINANCE_FACTOR = 4
        /** Corpus rank from which a dictionary entry stops being trusted
         *  as "the user meant this" — the subtitle corpus tail beyond
         *  this is riddled with typos and accentless spellings. */
        private const val WEAK_TYPED_RANK = 10_000
        /** A weak-known word only yields to its accented sibling when the
         *  sibling is at least this many times more frequent. */
        private const val WEAK_ACCENT_FACTOR = 8
        /** A weak-known word only yields to an edit-1 fix at least this
         *  many times more frequent than itself. */
        private const val WEAK_EDIT_FACTOR = 50
        /** Floor for the dominance comparison so a rank-0 winner doesn't
         *  make every runner-up look competitive. */
        private const val SCORE_FLOOR = 50
        /** Score base for user-dictionary words: count 2 lands mid-list,
         *  a word typed dozens of times competes with top corpus words. */
        private const val USER_RANK_BASE = 6_000
        /** Corpus rank below which a candidate is trusted without asking
         *  the validator (common words incl. colloquialisms Hunspell may
         *  not know — "tesó"). */
        private const val CANDIDATE_TRUST_RANK = 30_000
        /** How many top-scored candidates are considered (and validated)
         *  at most per keypress. */
        private const val CANDIDATE_VET_LIMIT = 24

        // --- Physical key adjacency ---------------------------------------

        private val adjacencyCache = HashMap<String, Map<Char, String>>()

        @Synchronized
        private fun adjacencyFor(langId: String): Map<Char, String> =
            adjacencyCache.getOrPut(langId) { buildAdjacency(keyboardRows(langId)) }

        /** Letter rows of the language's physical layout, padded so that
         *  vertical neighbours share column indices. */
        private fun keyboardRows(langId: String): Array<String> = when {
            // Hungarian QWERTZ: ö/ü/ó live on the number row above p/ő/ú.
            langId.startsWith("hu") -> arrayOf(
                "         öüó",
                "qwertzuiopőú",
                "asdfghjkléáű",
                "íyxcvbnm"
            )
            langId.startsWith("de") -> arrayOf(
                "qwertzuiopü",
                "asdfghjklöä",
                "yxcvbnm"
            )
            langId.startsWith("es") -> arrayOf(
                "qwertyuiop",
                "asdfghjklñ",
                "zxcvbnm"
            )
            else -> arrayOf(
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm"
            )
        }

        private fun buildAdjacency(rows: Array<String>): Map<Char, String> {
            val out = HashMap<Char, StringBuilder>()
            fun link(a: Char, b: Char) {
                if (a == ' ' || b == ' ' || a == b) return
                out.getOrPut(a) { StringBuilder() }.let { if (!it.contains(b)) it.append(b) }
                out.getOrPut(b) { StringBuilder() }.let { if (!it.contains(a)) it.append(a) }
            }
            for (r in rows.indices) {
                val row = rows[r]
                for (c in row.indices) {
                    if (c + 1 < row.length) link(row[c], row[c + 1])
                    if (r + 1 < rows.size) {
                        val below = rows[r + 1]
                        for (dc in -1..1) {
                            val j = c + dc
                            if (j in below.indices) link(row[c], below[j])
                        }
                    }
                }
            }
            return out.mapValues { it.value.toString() }
        }
    }
}
