package com.pckeyboard.ime.grammar

/**
 * Offline proofreading of a whole text: runs the correction engine
 * over every word (with the same previous-word context the live
 * auto-correct sees) and applies the AkH comma rules — the offline
 * counterpart of the cloud "Proofread" writing tools.
 *
 * Pure logic: the engine is injected as [correct], so this is fully
 * unit-testable and has no Android dependencies.
 */
class Proofreader(
    private val langId: String,
    /** Returns the replacement for [word] given the previous two words
     *  (post-correction, same sentence), or null to leave it alone. */
    private val correct: (word: String, prev: String?, prev2: String?) -> String?
) {

    data class Change(val original: String, val replacement: String)

    data class Result(
        val text: String,
        val wordFixes: List<Change>,
        val commasAdded: Int,
        /** True when the time budget ran out and the tail was copied
         *  through unchanged. */
        val truncated: Boolean
    ) {
        val changeCount: Int get() = wordFixes.size + commasAdded
    }

    private fun isWordChar(c: Char) = c.isLetter() || c == '\''

    fun proofread(text: String, budgetMs: Long = Long.MAX_VALUE / 4_000_000L): Result {
        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        val out = StringBuilder(text.length + 16)
        val fixes = ArrayList<Change>()
        var commas = 0
        var truncated = false

        var prev: String? = null
        var prev2: String? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!isWordChar(c)) {
                // Separator run: spaces/commas keep the word context,
                // anything else (sentence punctuation, newline, digits,
                // symbols) starts a fresh one.
                val start = i
                while (i < text.length && !isWordChar(text[i])) i++
                val sep = text.substring(start, i)
                out.append(sep)
                if (sep.any { it != ' ' && it != ',' }) { prev = null; prev2 = null }
                continue
            }
            val start = i
            while (i < text.length && isWordChar(text[i])) i++
            val word = text.substring(start, i)

            if (truncated || System.nanoTime() > deadline) {
                truncated = true
                out.append(word)
                prev2 = prev; prev = word
                continue
            }

            // Comma first: "hogy"/"de"/"mint"… after a plain word.
            if (CommaRules.shouldInsertCommaBefore(langId, out.toString(), word)) {
                val spaces = CommaRules.trailingSpaces(out.toString())
                out.setLength(out.length - spaces)
                out.append(',')
                repeat(spaces) { out.append(' ') }
                commas++
            }

            val replacement = if (isIdentifierLike(text, start, i)) null
                              else correct(word, prev, prev2)
            val finalWord = if (replacement != null && replacement != word) {
                fixes.add(Change(word, replacement))
                replacement
            } else word
            out.append(finalWord)
            prev2 = prev
            prev = finalWord
        }
        return Result(out.toString(), fixes, commas, truncated)
    }

    /** Words glued to digits, hyphens or URL/e-mail/path punctuation
     *  are identifiers or compounds, not prose — never "correct" them. */
    private fun isIdentifierLike(text: String, start: Int, end: Int): Boolean {
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(end)
        if (before != null && (before.isDigit() || before in "@_/\\#-+")) return true
        if (after != null && (after.isDigit() || after in "@_/\\#-+")) return true
        // "pelda.hu" / "www.pelda": a dot touching a letter on the far side.
        if (after == '.' && text.getOrNull(end + 1)?.let { it.isLetterOrDigit() } == true) return true
        if (before == '.' && text.getOrNull(start - 2)?.let { it.isLetterOrDigit() } == true) return true
        return false
    }
}
