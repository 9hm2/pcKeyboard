package com.pckeyboard.ime.view

/**
 * Converts spoken punctuation words inside a voice-recognition
 * transcript into the matching punctuation characters before the text
 * is committed to the editor.
 *
 *   "kérdezhetek valamit kérdőjel" → "kérdezhetek valamit?"
 *   "ez a vége pont új sor"        → "ez a vége.\n"
 *   "ask me anything question mark"→ "ask me anything?"
 *
 * The space before the recognised word is consumed so the punctuation
 * sits flush against the previous token, the way a human would type it.
 * Locale is resolved from the BCP-47 tag passed in by VoiceInputView;
 * unknown locales fall back to the combined HU + EN rule set so the
 * common cases still work.
 */
object VoicePunctuation {

    private data class Rule(val pattern: Regex, val replacement: String)

    /** Build a rule that consumes optional leading whitespace + the
     *  word(s), case-insensitively, with a word boundary at the end so
     *  e.g. "pont" doesn't eat into "pontosan". */
    private fun rule(word: String, mark: String): Rule {
        val escaped = Regex.escape(word).replace(" ", "\\s+")
        return Rule(
            Regex("(?:^|\\s+)$escaped(?=\\s|$|[\\p{Punct}])", RegexOption.IGNORE_CASE),
            mark
        )
    }

    /** Variant that only matches when the word sits at the very end of
     *  the transcript — used for words that double as ordinary nouns
     *  (Hungarian "pont", literally "point") so an inline noun isn't
     *  silently chopped into a period. */
    private fun endRule(word: String, mark: String): Rule {
        val escaped = Regex.escape(word).replace(" ", "\\s+")
        return Rule(
            Regex("(?:^|\\s+)$escaped\\s*$", RegexOption.IGNORE_CASE),
            mark
        )
    }

    private val HU: List<Rule> = listOf(
        // Multi-word patterns first so "új sor" wins over "új" + "sor".
        rule("új sor",       "\n"),
        rule("újsor",        "\n"),
        rule("új bekezdés",  "\n\n"),
        rule("új bekezdes",  "\n\n"),
        rule("kettőspont",   ":"),
        rule("kettospont",   ":"),
        rule("pontosvessző", ";"),
        rule("pontosvesszo", ";"),
        rule("kérdőjel",     "?"),
        rule("kerdojel",     "?"),
        rule("felkiáltójel", "!"),
        rule("felkialtojel", "!"),
        rule("vessző",       ","),
        rule("vesszo",       ","),
        // "pont" is a real Hungarian noun ("the point") so converting it
        // wherever it shows up eats inline words. Only convert it at the
        // very end of the utterance — when the user means a final stop.
        endRule("pont",      "."),
        rule("kötőjel",      "-"),
        rule("kotojel",      "-"),
        rule("gondolatjel",  " — "),
        rule("idézőjel",     "\""),
        rule("idezojel",     "\""),
        rule("aposztróf",    "'"),
        rule("aposztrof",    "'"),
    )

    private val EN: List<Rule> = listOf(
        rule("new line",          "\n"),
        rule("newline",           "\n"),
        rule("new paragraph",     "\n\n"),
        rule("question mark",     "?"),
        rule("exclamation mark",  "!"),
        rule("exclamation point", "!"),
        rule("semicolon",         ";"),
        rule("colon",             ":"),
        rule("comma",             ","),
        // "period" / "full stop" / "dot" double as ordinary nouns —
        // only convert at the end of the utterance.
        endRule("period",         "."),
        endRule("full stop",      "."),
        endRule("dot",            "."),
        rule("dash",              "-"),
        rule("hyphen",            "-"),
        rule("quote",             "\""),
    )

    private val DE: List<Rule> = listOf(
        rule("neue zeile",  "\n"),
        rule("neuer absatz", "\n\n"),
        rule("doppelpunkt", ":"),
        rule("semikolon",   ";"),
        rule("fragezeichen", "?"),
        rule("ausrufezeichen", "!"),
        rule("komma",       ","),
        // "Punkt" is a common German noun, only convert at end.
        endRule("punkt",    "."),
        rule("bindestrich", "-"),
        rule("anführungszeichen", "\""),
        rule("anfuehrungszeichen", "\""),
    )

    private val ES: List<Rule> = listOf(
        rule("nueva línea",            "\n"),
        rule("nueva linea",            "\n"),
        rule("nuevo párrafo",          "\n\n"),
        rule("nuevo parrafo",          "\n\n"),
        rule("signo de interrogación", "?"),
        rule("signo de interrogacion", "?"),
        rule("interrogación",          "?"),
        rule("interrogacion",          "?"),
        rule("signo de exclamación",   "!"),
        rule("signo de exclamacion",   "!"),
        rule("exclamación",            "!"),
        rule("exclamacion",            "!"),
        rule("dos puntos",             ":"),
        rule("punto y coma",           ";"),
        rule("coma",                   ","),
        // "punto" is a common Spanish noun, only convert at end.
        endRule("punto",               "."),
        rule("guion",                  "-"),
        rule("guión",                  "-"),
        rule("comillas",               "\""),
    )

    fun apply(text: String, locale: String): String {
        val rules = when {
            locale.startsWith("hu", ignoreCase = true) -> HU
            locale.startsWith("en", ignoreCase = true) -> EN
            locale.startsWith("de", ignoreCase = true) -> DE
            locale.startsWith("es", ignoreCase = true) -> ES
            else -> HU + EN
        }
        var s = text
        for (rule in rules) {
            s = rule.pattern.replace(s) { match ->
                // Preserve the leading character if it wasn't whitespace
                // (rare — e.g. punctuation right before the recognised
                // word) so we don't accidentally eat it.
                val prefix = match.value.firstOrNull()
                val keepPrefix = prefix != null && !prefix.isWhitespace()
                if (keepPrefix) "$prefix${rule.replacement}" else rule.replacement
            }
        }
        return s
    }
}
