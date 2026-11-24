package com.pckeyboard.ime.grammar

/**
 * Pure comma rules (AkH. 248–260 for Hungarian, plus German) shared by
 * live typing (the service inserts the comma as the conjunction is
 * committed) and the offline Proofread action (applied to whole texts)
 * — one rule set, one place.
 */
object CommaRules {

    /** Conjunctions that grammatically require a comma before them:
     *  clause boundaries, comparisons, illetve/valamint/avagy/azaz. */
    private val COMMA_BEFORE: Map<String, Set<String>> = mapOf(
        "hu_HU" to setOf(
            "hogy", "de", "mert", "hanem", "viszont", "ugyanis",
            "hiszen", "mivel", "ami", "aki", "amit", "akit",
            "amely", "amelyik", "ahol", "amikor", "ahogy", "amiért",
            "mint", "illetve", "valamint", "avagy", "azaz",
            "vagyis", "ámde", "mintha"
        ),
        "de_DE" to setOf(
            "dass", "weil", "aber", "sondern", "obwohl", "damit",
            "denn", "wenn", "falls", "während", "sodass"
        )
    )

    /** Paired coordinating conjunctions: standalone they take no comma
     *  ("ezt vagy azt"), but the SECOND occurrence within one sentence
     *  does ("vagy ezt, vagy azt"). */
    private val PAIRED: Map<String, Set<String>> = mapOf(
        "hu_HU" to setOf("vagy", "akár", "hol", "mind", "sem", "se"),
        "de_DE" to setOf("oder", "entweder", "weder", "noch")
    )

    /** "több mint száz" style quantity comparisons take NO comma. */
    private val MINT_NO_COMMA_PREV = setOf("több", "kevesebb", "jobb", "rosszabb")

    /** Words after which no comma is inserted — coordinating
     *  conjunctions form comma-free chains, and a conjunction stacked on
     *  a conjunction never takes one. */
    private val NO_COMMA_AFTER: Map<String, Set<String>> = mapOf(
        "hu_HU" to (setOf("és", "s", "vagy", "meg", "akár", "se", "sem") +
            COMMA_BEFORE.getValue("hu_HU")),
        "de_DE" to (setOf("und", "oder", "sowie", "bzw", "beziehungsweise", "wie") +
            COMMA_BEFORE.getValue("de_DE"))
    )

    private fun isWordChar(c: Char) = c.isLetter() || c == '\''

    /** True when the language has any comma rules at all. */
    fun supports(langId: String) = COMMA_BEFORE.containsKey(langId)

    /**
     * Should a comma be inserted before [word]? [prefix] is ALL text
     * before the word (including the space(s) separating it from the
     * previous word). The decision needs: a separating space, a plain
     * word right before it (so never at text start or after
     * punctuation), the previous word not being a coordinator, and the
     * paired / "több mint" special cases.
     */
    fun shouldInsertCommaBefore(langId: String, prefix: String, word: String): Boolean {
        val commaBefore = COMMA_BEFORE[langId] ?: return false
        val paired = PAIRED[langId] ?: emptySet()
        val lower = word.lowercase()
        if (word.isEmpty() || (lower !in commaBefore && lower !in paired)) return false

        var i = prefix.length
        while (i > 0 && prefix[i - 1] == ' ') i--
        val spaces = prefix.length - i
        if (spaces == 0 || i == 0 || !isWordChar(prefix[i - 1])) return false
        var j = i
        while (j > 0 && isWordChar(prefix[j - 1])) j--
        val prevWord = prefix.substring(j, i).lowercase()
        if (prevWord in (NO_COMMA_AFTER[langId] ?: emptySet())) return false

        return if (lower in commaBefore) {
            !(lower == "mint" && prevWord in MINT_NO_COMMA_PREV)
        } else {
            sentenceContains(prefix, lower)
        }
    }

    /** Number of space characters directly before the end of [prefix]. */
    fun trailingSpaces(prefix: String): Int {
        var i = prefix.length
        while (i > 0 && prefix[i - 1] == ' ') i--
        return prefix.length - i
    }

    /** True when [word] occurs as a standalone word in the current
     *  sentence (scanning [prefix] back to the last sentence ender). */
    private fun sentenceContains(prefix: String, word: String): Boolean {
        val sentenceStart = prefix.indexOfLast { it == '.' || it == '!' || it == '?' } + 1
        val sentence = prefix.substring(sentenceStart).lowercase()
        var idx = 0
        while (idx < sentence.length) {
            while (idx < sentence.length && !isWordChar(sentence[idx])) idx++
            val start = idx
            while (idx < sentence.length && isWordChar(sentence[idx])) idx++
            if (idx > start && sentence.substring(start, idx) == word) return true
        }
        return false
    }
}
