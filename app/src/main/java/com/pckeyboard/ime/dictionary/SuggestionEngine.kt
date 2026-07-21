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
    private val validator: ((String) -> Boolean)? = null,
    /** Word-pair context model: candidates that actually follow the
     *  previous word in real text get boosted, so ties between
     *  corrections resolve the way the sentence wants. Null while
     *  loading — behaves as before. */
    private val bigrams: BigramModel? = null,
    /** Word-triple context model — two words of preceding context,
     *  a much sharper signal where it fires. Null while loading. */
    private val trigrams: TrigramModel? = null,
    /** Neural char-LM scorer: (context, candidate) -> avg log-prob per
     *  char, or null on failure. Only consulted on deep (word-boundary)
     *  calls. Null until a trained model ships in assets/reranker/. */
    private val reranker: ((String, String) -> Double?)? = null,
    /** The user's personal typo model: remembered typed->meant pairs
     *  and per-slip statistics learned from suggestion taps. */
    private val personal: PersonalSignals? = null,
    /** Hunspell's morphological suggestion generator. The corpus list
     *  can only propose words somebody once wrote; this reaches every
     *  form the affix rules can build ("gondolkodhattam"). Consulted
     *  only on deep calls and only when the fast sources came up weak
     *  — its internal time budget makes it too slow per keystroke. */
    private val morphSuggester: ((String) -> List<String>)? = null,
    /** Cross-language trust: true when ANOTHER enabled language knows
     *  this word. Bilingual typing (the Samsung behaviour): an English
     *  word typed while the Hungarian layout is active must never be
     *  "corrected" into Hungarian. */
    private val foreignTrust: ((String) -> Boolean)? = null,
    /** The user's own word-pair habits — context boost stronger than
     *  the corpus models, because it's literally how this user writes. */
    private val personalBigrams: PersonalBigramSignals? = null
) {

    data class Result(
        /** Display candidates, best first, cased like the typed word. */
        val suggestions: List<String>,
        /** Replacement to apply on word commit in auto-correct mode, or
         *  null when nothing is confident enough. Cased like the input. */
        val autoReplace: String?
    )

    private val adjacency: Map<Char, String> = adjacencyFor(dict.langId)

    fun suggest(
        typed: String,
        maxSuggestions: Int = 3,
        prevWord: String? = null,
        prev2Word: String? = null,
        /** True on word-boundary (commit) calls — unlocks the neural
         *  rerank, which is too slow to run per keystroke. */
        deep: Boolean = false
    ): Result {
        if (typed.length < MIN_TYPED_LENGTH) return EMPTY
        val lower = typed.lowercase()
        if (lower.any { !it.isLetter() && it != '\'' }) return EMPTY
        val prevRank = prevWord?.lowercase()?.let { dict.rankOf(it) } ?: -1
        val prev2Rank = prev2Word?.lowercase()?.let { dict.rankOf(it) } ?: -1

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
            user?.isKnown(lower) == true ||
            foreignTrust?.let { it(typed) || (typed != lower && it(lower)) } == true

        // Reverse accent error ("mindíg" → "mindig"): the typed word is
        // morphologically WRONG but its accent-stripped skeleton is a
        // valid word. This class bypasses rank-trust — "mindíg" is such
        // a common misspelling that it sits high in the subtitle corpus.
        // Only possible with the validator loaded: without a judge,
        // stripping deliberate accents would be far too dangerous.
        val skeletonWord = WordDictionary.deaccent(lower)
        val deaccentFix = validator != null && !typedValidWord &&
            skeletonWord != lower && lower.length >= DEACCENT_FIX_MIN_LENGTH &&
            user?.isKnown(lower) != true &&
            validator.invoke(skeletonWord)

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

        if (deaccentFix) {
            val skeletonRank = dict.rankOf(skeletonWord)
            offer(skeletonWord, if (skeletonRank >= 0) skeletonRank / ACCENT_BOOST else 0)
        }

        if (!typedTrusted && lower.length >= MIN_EDIT1_LENGTH) {
            val edit1 = editDistance1Hits(lower)
            for ((word, score) in edit1) {
                offer(word, score)
            }
            // Two-typo words ("teljrsem", "krllrne", "afjom") have no
            // edit-1 neighbour at all — scan the dictionary for
            // distance-2 matches when the cheap pass came up (near)
            // empty. Word boundaries only: mid-word, a two-edit
            // whole-word "fix" of a prefix is junk ("havít…" is not a
            // typo of "hát"), and completions should own the strip.
            val bestEdit1 = edit1.minOfOrNull { it.second } ?: Int.MAX_VALUE
            if (deep && lower.length >= MIN_EDIT2_LENGTH && bestEdit1 > EDIT2_TRIGGER_SCORE) {
                for ((word, score) in editDistance2Scan(lower)) {
                    offer(word, score)
                }
            }
        }

        val completions = dict.topCompletions(lower, maxSuggestions)
        for (completion in completions) {
            offer(completion, dict.rankOf(completion) * COMPLETION_PENALTY)
        }
        // Exact-prefix completion dies when the typo sits in the first
        // letters of a word still being typed ("havít…") — fall back to
        // typo-tolerant prefix matching, weighted more cautiously.
        if (completions.isEmpty() && !typedTrusted && lower.length >= 3) {
            for (completion in dict.fuzzyCompletions(lower, maxSuggestions)) {
                offer(completion, dict.rankOf(completion) * FUZZY_COMPLETION_PENALTY)
            }
        }

        // The user's own words complete too — scored by personal use
        // count, so a name typed daily quickly outranks corpus words.
        user?.knownCompletions(lower, maxSuggestions)?.forEach { (word, count) ->
            offer(word, userScore(count))
        }

        // The personal typo memory: if the user has picked a correction
        // for exactly this typed word before, it's the strongest
        // possible signal — every confirmation pushes it further up.
        val personalFix = if (!typedTrusted) personal?.correctionFor(lower) else null
        if (personalFix != null && personalFix.first != lower) {
            offer(personalFix.first, PERSONAL_PAIR_BASE / personalFix.second)
        }

        // Morphological fallback: when the fast sources found nothing
        // convincing for a wrong word, ask Hunspell to GENERATE fixes
        // from stems + affix rules — the only source that reaches words
        // the corpus never saw. Scored by corpus rank when available,
        // by a flat "rare but real" score otherwise, degraded by edit
        // distance from what was typed.
        if (deep && morphSuggester != null && !typedTrusted &&
            lower.length >= MIN_AUTOREPLACE_LENGTH &&
            (scored.values.minOrNull() ?: Int.MAX_VALUE) > MORPH_TRIGGER_SCORE
        ) {
            val skeleton = WordDictionary.deaccent(lower)
            for (raw in morphSuggester.invoke(lower).take(MORPH_MAX_CANDIDATES)) {
                val cand = raw.lowercase()
                if (cand == lower || cand.any { !it.isLetter() && it != '\'' }) continue
                val d = boundedEditDistance(skeleton, WordDictionary.deaccent(cand), 3)
                if (d > 3) continue
                val rank = dict.rankOf(cand)
                val base = if (rank >= 0) rank else MORPH_UNRANKED_SCORE
                offer(cand, (base * (1.0 + MORPH_DISTANCE_PENALTY * (d - 1))).toInt())
            }
        }

        if (scored.isEmpty()) return EMPTY
        // Context boost: a candidate that actually follows the previous
        // word in real text gets its score divided — this is what breaks
        // ties between otherwise equally plausible corrections in the
        // direction the sentence wants.
        if (prevWord != null &&
            (bigrams != null || trigrams != null || personalBigrams != null)
        ) {
            for (entry in scored.entries) {
                val candRank = dict.rankOf(entry.key)
                val c2 = if (prevRank >= 0) bigrams?.count(prevRank, candRank) ?: 0 else 0
                val c3 = if (prevRank >= 0)
                    trigrams?.count(prev2Rank, prevRank, candRank) ?: 0 else 0
                val cp = personalBigrams?.count(prevWord, entry.key) ?: 0
                if (c2 > 0 || c3 > 0 || cp > 0) {
                    val divisor = 1.0 +
                        kotlin.math.ln(1.0 + c2) * BIGRAM_BOOST +
                        kotlin.math.ln(1.0 + c3) * TRIGRAM_BOOST +
                        kotlin.math.ln(1.0 + cp) * PERSONAL_BIGRAM_BOOST
                    entry.setValue((entry.value / divisor).toInt())
                }
            }
        }
        // Deep-tail candidates must pass the morphological validator (when
        // available) — this keeps corpus junk out of the strip AND out of
        // auto-correction. Bounded to the best few dozen entries so a
        // pathological word can't trigger hundreds of spell() calls.
        val ranked = scored.entries.sortedBy { it.value }
            .take(CANDIDATE_VET_LIMIT)
            .filter { candidateAcceptable(it.key) }

        if (ranked.isEmpty()) return EMPTY
        // Neural rerank (word boundaries only): the char-LM scores the
        // top candidates in sentence context; scores are folded back
        // multiplicatively so the engine's own evidence still counts.
        val finalRanked = if (deep && reranker != null && ranked.size > 1) {
            val ctx = listOfNotNull(prev2Word, prevWord).joinToString(" ")
            val lps = HashMap<String, Double>()
            for (e in ranked.take(RERANK_CANDIDATE_LIMIT)) {
                reranker.invoke(ctx, e.key)?.let { lps[e.key] = it }
            }
            if (lps.size < 2) ranked
            else {
                val lpMax = lps.values.max()
                ranked.map { e ->
                    val lp = lps[e.key]
                    val adjusted =
                        if (lp == null) e.value
                        else (e.value *
                            kotlin.math.exp((lpMax - lp) * RERANKER_BETA)).toInt()
                    java.util.AbstractMap.SimpleEntry(e.key, adjusted)
                        as Map.Entry<String, Int>
                }.sortedBy { it.value }
            }
        } else ranked
        val autoReplace =
            if (deaccentFix && lower.length >= MIN_AUTOREPLACE_LENGTH) skeletonWord
            else pickAutoReplace(
                lower, typedRank, typedTrusted, accentRank, finalRanked,
                personalFix
            )
        return Result(
            suggestions = finalRanked.take(maxSuggestions).map { matchCase(typed, it.key) },
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
        fun pf(key: String): Float = personal?.editFactor(key) ?: 1f
        // Probe in ascending-penalty order so when two edit paths produce
        // the same string, the cheaper interpretation wins the seen-set.
        // Adjacent transpositions — the classic fast-typing slip.
        for (i in 0 until n - 1) {
            if (word[i] == word[i + 1]) continue
            sb.setLength(0); sb.append(word)
            sb.setCharAt(i, word[i + 1]); sb.setCharAt(i + 1, word[i])
            probe(sb.toString(), PENALTY_TRANSPOSE * pf("t:${word[i]}${word[i + 1]}"))
        }
        // Deletions (an extra character slipped in).
        for (i in 0 until n) {
            probe(
                StringBuilder(word).deleteCharAt(i).toString(),
                PENALTY_DELETE * pf("d:${word[i]}")
            )
        }
        // Insertions (a character was skipped).
        for (i in 0..n) {
            for (c in dict.alphabet) {
                sb.setLength(0); sb.append(word)
                sb.insert(i, c)
                probe(sb.toString(), PENALTY_INSERT * pf("i:$c"))
            }
        }
        // Substitutions — cheap for neighbouring keys / accent siblings,
        // expensive for keys from the other side of the keyboard; the
        // user's own recurring slips get cheaper still.
        for (i in 0 until n) {
            for (c in dict.alphabet) {
                if (c == word[i]) continue
                sb.setLength(0); sb.append(word)
                sb.setCharAt(i, c)
                val penalty = if (isNearMiss(word[i], c)) PENALTY_SUB_NEAR else PENALTY_SUB_FAR
                probe(sb.toString(), penalty * pf("s:${word[i]}$c"))
            }
        }
        return hits
    }

    /**
     * Distance-2 fallback: scans the most frequent [EDIT2_SCAN_CAP]
     * dictionary words for skeleton edit distance exactly 2 from the
     * typed word. First character must match (two-typo words almost
     * never start wrong — and it cuts the scan cost dramatically) and
     * lengths may differ by at most 2. Scored with a heavy penalty so a
     * distance-1 fix always wins when one exists.
     */
    private fun editDistance2Scan(word: String): List<Pair<String, Int>> {
        val skeleton = WordDictionary.deaccent(word)
        val first = skeleton[0]
        val hits = ArrayList<Pair<String, Int>>()
        val limit = minOf(EDIT2_SCAN_CAP, dict.size)
        for (rank in 0 until limit) {
            val candSkeleton = dict.skeletonAt(rank)
            if (candSkeleton.isEmpty()) continue
            // First char may differ — but only by a plausible slip
            // (neighbouring key / accent sibling), otherwise the scan
            // cost explodes and the matches are junk anyway.
            if (candSkeleton[0] != first && !isNearMiss(word[0], candSkeleton[0])) continue
            if (kotlin.math.abs(candSkeleton.length - skeleton.length) > 2) continue
            val d = boundedEditDistance(skeleton, candSkeleton, 2)
            if (d == 2) {
                val cand = dict.wordAt(rank)
                hits.add(cand to (rank * edit2Penalty(word, cand)).toInt())
            }
        }
        return hits
    }

    /** Slip plausibility for a distance-2 candidate. Two same-length
     *  substitutions of NEIGHBOURING keys are the classic fat-finger
     *  double ("afjom"→"adjon": f→d, m→n) — barely dearer than a single
     *  slip. Far-key substitutions multiply up, and length-changing
     *  fixes (an extra/lost letter plus another edit) are the least
     *  likely story, so they carry the heaviest flat penalty. */
    private fun edit2Penalty(typed: String, cand: String): Float {
        if (typed.length != cand.length) return PENALTY_EDIT2_LENGTH
        var penalty = PENALTY_EDIT2
        var mismatches = 0
        for (i in typed.indices) {
            val a = typed[i].lowercaseChar()
            val b = cand[i]
            if (WordDictionary.deaccent(a.toString()) ==
                WordDictionary.deaccent(b.toString())
            ) continue
            mismatches++
            if (mismatches > 2) return PENALTY_EDIT2_LENGTH  // transposition-ish shape
            penalty *= (if (isNearMiss(a, b)) 1.0f else PENALTY_EDIT2_FAR_SUB) *
                (personal?.editFactor("s:$a$b") ?: 1f)
        }
        return penalty
    }

    /**
     * Banded Damerau-Levenshtein (optimal string alignment): returns the
     * edit distance if it's ≤ [max], or [Int.MAX_VALUE] otherwise. Only
     * cells within [max] of the diagonal are computed.
     */
    private fun boundedEditDistance(a: String, b: String, max: Int): Int {
        val la = a.length; val lb = b.length
        if (kotlin.math.abs(la - lb) > max) return Int.MAX_VALUE
        if (a == b) return 0
        val inf = max + 1
        var prevPrev = IntArray(lb + 1) { inf }
        var prev = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val cur = IntArray(lb + 1) { inf }
            cur[0] = i
            val from = maxOf(1, i - max)
            val to = minOf(lb, i + max)
            for (j in from..to) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(
                    prev[j] + 1,          // deletion
                    cur[j - 1] + 1,       // insertion
                    prev[j - 1] + cost    // substitution / match
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prevPrev[j - 2] + 1)   // transposition
                }
                cur[j] = v
            }
            if ((from..to).all { cur[it] > max } && cur[0] > max) return Int.MAX_VALUE
            prevPrev = prev
            prev = cur
        }
        return if (prev[lb] <= max) prev[lb] else Int.MAX_VALUE
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
        ranked: List<Map.Entry<String, Int>>,
        personalFix: Pair<String, Int>? = null
    ): String? {
        if (typedTrusted) return null                  // the word is fine as typed
        if (lower.length < MIN_AUTOREPLACE_LENGTH) return null
        val best = ranked.firstOrNull() ?: return null
        // A correction the user has personally confirmed at least twice
        // for exactly this typed word wins outright — no distance or
        // frequency gate can know better than the user's own taps.
        if (personalFix != null && personalFix.second >= PERSONAL_PAIR_AUTO_MIN &&
            personalFix.first == best.key
        ) return best.key
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

        // Typo correction: the winner must be common (cap scales with
        // word length — long words have few close neighbours, so deeper
        // corrections are safe) OR morphologically valid per Hunspell,
        // and clearly dominate the runner-up on weighted score.
        // Distances are measured on deaccented skeletons so "typos +
        // missing accents" combine cleanly; distance 2 is allowed for
        // words of 5+ characters ("teljrsem" → "teljesen").
        val bestRank = dict.rankOf(best.key)
        val rankCap = when {
            lower.length >= 6 -> EDIT1_AUTOREPLACE_MAX_RANK_LONG
            lower.length == 5 -> EDIT1_AUTOREPLACE_MAX_RANK_MID
            else -> EDIT1_AUTOREPLACE_MAX_RANK_SHORT
        }
        val bestIsUserWord = (user?.knownCount(best.key) ?: 0) > 0
        val bestIsValidWord = validator?.let { v ->
            v(best.key) || v(best.key.replaceFirstChar { it.uppercase() })
        } == true
        if (bestRank !in 0 until rankCap && !bestIsUserWord && !bestIsValidWord) return null
        val skeleton = WordDictionary.deaccent(lower)
        val bestSkeleton = WordDictionary.deaccent(best.key)
        val maxDistance = if (lower.length >= MIN_EDIT2_LENGTH) 2 else 1
        val bestDistance = boundedEditDistance(skeleton, bestSkeleton, maxDistance)
        if (bestDistance > maxDistance) return null
        if (weakKnown) {
            // Accent-only differences must go through the accent branches
            // above (protects deliberate accents: "vágyok" never becomes
            // "vagyok"), and a real typo fix must be far more common
            // than the deep-tail "word" it replaces.
            if (skeleton == bestSkeleton) return null
            if (bestRank < 0 || bestRank * WEAK_EDIT_FACTOR > typedRank) return null
        }
        val second = ranked.getOrNull(1)
        if (second != null &&
            second.value * DOMINANCE_DEN <=
                maxOf(best.value, SCORE_FLOOR) * DOMINANCE_NUM &&
            boundedEditDistance(
                skeleton, WordDictionary.deaccent(second.key), maxDistance
            ) <= bestDistance
        ) return null                                  // an equally close, plausible rival — stay passive
        return best.key
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
        /** Typo-tolerant completions are more uncertain still. */
        private const val FUZZY_COMPLETION_PENALTY = 4
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

        /** Words this long or longer may be corrected across TWO edits
         *  ("teljrsem" → "teljesen"); shorter words stay at one — half
         *  of a 4-letter word being wrong is a different word, not a
         *  slip. Also gates the distance-2 dictionary scan. */
        private const val MIN_EDIT2_LENGTH = 5
        /** How deep the distance-2 scan looks into the rank-ordered
         *  dictionary — the full list: the first-char and length
         *  filters cut ~97% of rows before any distance is computed,
         *  so even the whole 400k stays a few ms. */
        private const val EDIT2_SCAN_CAP = 400_000
        /** Base penalty for a same-length double-substitution fix. */
        private const val PENALTY_EDIT2 = 1.4f
        /** Extra multiplier per far-key substitution in a distance-2 fix. */
        private const val PENALTY_EDIT2_FAR_SUB = 1.8f
        /** Flat penalty for length-changing distance-2 fixes. */
        private const val PENALTY_EDIT2_LENGTH = 3.0f
        /** The distance-2 scan only runs when edit-1 found nothing
         *  better than this score — a decent one-slip fix wins anyway. */
        private const val EDIT2_TRIGGER_SCORE = 20_000
        /** Minimum length for the reverse accent fix ("mindíg" →
         *  "mindig"); very short accented words are too easy to hit. */
        private const val DEACCENT_FIX_MIN_LENGTH = 4

        private const val ACCENT_AUTOREPLACE_MAX_RANK_SHORT = 60_000
        private const val ACCENT_AUTOREPLACE_MAX_RANK_LONG = 250_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_SHORT = 30_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_MID = 80_000
        private const val EDIT1_AUTOREPLACE_MAX_RANK_LONG = 150_000
        /** Runner-up within bestScore×(NUM/DEN) counts as "competing" —
         *  a tight margin: only a genuine coin-flip stays passive, a
         *  meaningfully better-scored fix gets applied. */
        private const val DOMINANCE_NUM = 6
        private const val DOMINANCE_DEN = 5
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
        /** Score base for a personally confirmed typo->fix pair: one tap
         *  puts it near the top, repeats pin it there. */
        private const val PERSONAL_PAIR_BASE = 600
        /** Confirmation taps needed before a personal pair may
         *  auto-replace on its own authority. */
        private const val PERSONAL_PAIR_AUTO_MIN = 2
        /** The morphological fallback only runs when no fast-source
         *  candidate scored better than this — i.e. the fast sources
         *  came up empty or with junk. A decent fast answer must not be
         *  second-guessed by unranked morph siblings, which used to
         *  create fake ties that blocked good corrections. */
        private const val MORPH_TRIGGER_SCORE = 100_000
        /** At most this many Hunspell suggestions are considered. */
        private const val MORPH_MAX_CANDIDATES = 5
        /** Effective score for a morph candidate the corpus has no rank
         *  for — "rare but real". */
        private const val MORPH_UNRANKED_SCORE = 40_000
        /** Score multiplier growth per extra edit of distance. */
        private const val MORPH_DISTANCE_PENALTY = 0.6
        /** Corpus rank below which a candidate is trusted without asking
         *  the validator (common words incl. colloquialisms Hunspell may
         *  not know — "tesó"). */
        private const val CANDIDATE_TRUST_RANK = 30_000
        /** How many top-scored candidates are considered (and validated)
         *  at most per keypress. */
        private const val CANDIDATE_VET_LIMIT = 24
        /** Strength of the bigram context boost: score is divided by
         *  1 + ln(1+count)×this, so a pair seen ~20 times in the corpus
         *  is ~7× more convincing. */
        private const val BIGRAM_BOOST = 2.0
        /** Trigram context is sharper than bigram — boost accordingly. */
        private const val TRIGRAM_BOOST = 3.0
        /** The user's own pairs beat corpus statistics. */
        private const val PERSONAL_BIGRAM_BOOST = 4.0
        /** How many top candidates the neural reranker scores per
         *  boundary. */
        private const val RERANK_CANDIDATE_LIMIT = 6
        /** Weight of the LM's opinion: per-char log-prob deltas are
         *  exponentiated with this factor onto the engine score.
         *  Calibrated against the eval harness once a model ships. */
        private const val RERANKER_BETA = 2.0

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
