package com.pckeyboard.ime.dictionary

import org.apache.lucene.analysis.hunspell.Dictionary as HunspellDictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.Random

/**
 * Offline quality benchmark — NOT part of the normal suite. Run with
 *   PCK_BENCH=1 PCK_BENCH_CORPORA=a.txt,b.txt PCK_BENCH_OUT=out.txt \
 *     gradle :app:testDebugUnitTest --tests '*QualityBenchmark*'
 * Measures (1) vocabulary coverage on held-out text and (2) typo
 * correction / completion quality by error class and by whether the
 * intended word is in the frequency list, only in Hunspell, or unknown.
 */
class QualityBenchmark {

    private val rows = arrayOf("         öüó", "qwertzuiopőú", "asdfghjkléáű", "íyxcvbnm")
    private val adj: Map<Char, List<Char>> = run {
        val m = HashMap<Char, MutableList<Char>>()
        fun link(a: Char, b: Char) {
            if (a == ' ' || b == ' ' || a == b) return
            m.getOrPut(a) { mutableListOf() }.let { if (b !in it) it.add(b) }
            m.getOrPut(b) { mutableListOf() }.let { if (a !in it) it.add(a) }
        }
        for (r in rows.indices) for (c in rows[r].indices) {
            if (c + 1 < rows[r].length) link(rows[r][c], rows[r][c + 1])
            if (r + 1 < rows.size) for (dc in -1..1) {
                val j = c + dc
                if (j in rows[r + 1].indices) link(rows[r][c], rows[r + 1][j])
            }
        }
        m
    }
    private val deacc = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ö' to 'o',
        'ő' to 'o', 'ú' to 'u', 'ü' to 'u', 'ű' to 'u')

    private fun noise(w: String, kind: String, rnd: Random): String? {
        val i = rnd.nextInt(w.length)
        return when (kind) {
            "sub" -> adj[w[i]]?.let { w.substring(0, i) + it[rnd.nextInt(it.size)] + w.substring(i + 1) }
            "sub_first" -> adj[w[0]]?.let { it[rnd.nextInt(it.size)] + w.substring(1) }
            "swap" -> if (i < w.length - 1 && w[i] != w[i + 1])
                w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2) else null
            "missing" -> if (w.length > 3) w.removeRange(i, i + 1) else null
            "extra" -> w.substring(0, i) + w[i] + w.substring(i)
            "accent" -> if (w.any { it in deacc }) w.map { deacc[it] ?: it }.joinToString("") else null
            "two" -> noise(w, "sub", rnd)?.let { a ->
                val j = rnd.nextInt(a.length)
                adj[a[j]]?.let { a.substring(0, j) + it[rnd.nextInt(it.size)] + a.substring(j + 1) }
            }
            else -> null
        }
    }

    @Test
    fun run() {
        assumeTrue(System.getenv("PCK_BENCH") != null)
        val out = StringBuilder()
        fun p(s: String = "") { out.appendLine(s); println(s) }

        val dictPath = System.getenv("PCK_BENCH_DICT") ?: "src/main/assets/dict/hu_HU.dict"
        val noNgram = System.getenv("PCK_BENCH_NONGRAM") != null
        val rt = Runtime.getRuntime()
        System.gc(); val before = rt.totalMemory() - rt.freeMemory()
        val dict = FileInputStream(File(dictPath)).use {
            WordDictionary.fromGzipStream("hu_HU", it)
        }!!
        System.gc(); val after = rt.totalMemory() - rt.freeMemory()
        p("DICT $dictPath words=${dict.size} heap≈%.1f MB".format((after - before) / 1048576.0))
        val bigrams = if (noNgram) null else FileInputStream(File("src/main/assets/dict/hu_HU.bigrams")).use {
            BigramModel.fromGzipStream(it)
        }
        val trigrams = if (noNgram) null else FileInputStream(File("src/main/assets/dict/hu_HU.trigrams")).use {
            TrigramModel.fromGzipStream(it)
        }
        val hs = File("src/main/assets/hunspell")
        val checker = FileInputStream(File(hs, "hu_HU.aff")).use { aff ->
            FileInputStream(File(hs, "hu_HU.dic")).use { dic ->
                Hunspell(HunspellDictionary(ByteBuffersDirectory(), "hu_HU", aff, dic))
            }
        }
        fun valid(w: String) = try {
            checker.spell(w) || checker.spell(w.replaceFirstChar { it.uppercase() })
        } catch (_: Throwable) { true }
        val engine = SuggestionEngine(
            dict, null, validator = { w -> try { checker.spell(w) } catch (_: Throwable) { true } },
            bigrams = bigrams, trigrams = trigrams,
            morphSuggester = { w -> try { checker.suggest(w) } catch (_: Throwable) { emptyList() } }
        )

        val corpora = System.getenv("PCK_BENCH_CORPORA").split(",")
        val maxCases = (System.getenv("PCK_BENCH_CASES") ?: "1500").toInt()
        val wordRe = Regex("[a-záéíóöőúüű]+")

        for (corpus in corpora) {
            p("==================== ${File(corpus).name} ====================")
            val sentences = File(corpus).useLines { seq ->
                seq.map { it.substringAfter('\t') }.take(120_000).toList()
            }
            // ---------------- coverage ----------------
            val typeCount = HashMap<String, Int>()
            var tokens = 0
            for (s in sentences) for (m in wordRe.findAll(s.lowercase())) {
                if (m.value.length < 2) continue
                typeCount.merge(m.value, 1, Int::plus); tokens++
            }
            var tDict = 0; var tHs = 0; var tEither = 0; var tNone = 0
            var yDict = 0; var yEither = 0
            val missing = ArrayList<Pair<String, Int>>()
            for ((w, c) in typeCount) {
                val inDict = dict.rankOf(w) >= 0
                val inHs = !inDict && valid(w)
                if (inDict) { tDict += c; yDict++ }
                if (inHs) tHs += c
                if (inDict || inHs) { tEither += c; yEither++ } else { tNone += c; missing.add(w to c) }
            }
            fun pct(a: Int, b: Int) = "%.2f%%".format(100.0 * a / b)
            p("COVERAGE tokens=$tokens types=${typeCount.size}")
            p("  token coverage: dict400k=${pct(tDict, tokens)}  +hunspell-only=${pct(tHs, tokens)}  either=${pct(tEither, tokens)}  NEITHER=${pct(tNone, tokens)}")
            p("  type  coverage: dict400k=${pct(yDict, typeCount.size)}  either=${pct(yEither, typeCount.size)}")
            p("  top missing (neither dict nor hunspell), by freq:")
            p("   " + missing.sortedByDescending { it.second }.take(40)
                .joinToString(", ") { "${it.first}(${it.second})" })

            // ---------------- correction benchmark ----------------
            val rnd = Random(7)
            val kinds = listOf("sub", "sub_first", "swap", "missing", "extra", "accent", "two")
            class Acc { var n = 0; var top1 = 0; var top3 = 0; var top8 = 0
                var autoOk = 0; var autoWrong = 0; var autoNone = 0 }
            val byKind = HashMap<String, Acc>()
            val byBucket = HashMap<String, Acc>()
            val control = Acc()
            val ctrlBucket = HashMap<String, Acc>()
            val completion = HashMap<String, Acc>()
            var emptyN = 0; var emptyValid = 0; var emptyDictTop = 0; var emptyOther = 0
            val emptyOtherEx = ArrayList<String>()
            var typoHsValid = 0; var typoInDict = 0; var typoTotal = 0
            var typoHsValidAndGoldInDict = 0
            val falseValidExamples = ArrayList<String>()
            val wrongExamples = ArrayList<String>()
            val missExamples = ArrayList<String>()
            var cases = 0
            var guard = 0
            while (cases < maxCases && guard++ < maxCases * 40) {
                val toks = wordRe.findAll(sentences[rnd.nextInt(sentences.size)].lowercase())
                    .map { it.value }.toList()
                if (toks.size < 4) continue
                val ix = 2 + rnd.nextInt(toks.size - 2)
                val gold = toks[ix]
                if (gold.length < 4 || gold.length > 14) continue
                val prev = toks[ix - 1]; val prev2 = toks[ix - 2]
                val bucket = when {
                    dict.rankOf(gold) >= 0 -> "gold in dict400k"
                    valid(gold) -> "gold hunspell-only"
                    else -> "gold unknown(name/rare)"
                }
                // control: untouched word must NOT be auto-changed
                run {
                    val r = engine.suggest(gold, 8, prev, prev2, deep = true)
                    val a = ctrlBucket.getOrPut(bucket) { Acc() }
                    a.n++; control.n++
                    val ar = r.autoReplace
                    if (ar != null && !ar.equals(gold, true)) {
                        a.autoWrong++; control.autoWrong++
                        if (wrongExamples.size < 25) wrongExamples.add("$gold -> $ar  [$bucket]")
                    }
                }
                val kind = kinds[rnd.nextInt(kinds.size)]
                val typo = noise(gold, kind, rnd) ?: continue
                if (typo == gold) continue
                cases++
                typoTotal++
                val hsOk = try { checker.spell(typo) } catch (_: Throwable) { true }
                if (hsOk) {
                    typoHsValid++
                    if (dict.rankOf(gold) >= 0) typoHsValidAndGoldInDict++
                    if (falseValidExamples.size < 30) falseValidExamples.add("$typo (want $gold)")
                }
                if (dict.rankOf(typo) >= 0) typoInDict++
                val r = engine.suggest(typo, 8, prev, prev2, deep = true)
                if (r.suggestions.isEmpty() && dict.rankOf(gold) >= 0) {
                    emptyN++
                    when {
                        hsOk -> emptyValid++
                        dict.rankOf(typo) in 0 until 10_000 -> emptyDictTop++
                        else -> { emptyOther++; if (emptyOtherEx.size < 25) emptyOtherEx.add("$typo(want $gold)") }
                    }
                }
                for (acc in listOf(byKind.getOrPut(kind) { Acc() }, byBucket.getOrPut(bucket) { Acc() })) {
                    acc.n++
                    val s = r.suggestions.map { it.lowercase() }
                    if (s.firstOrNull() == gold) acc.top1++
                    if (gold in s.take(3)) acc.top3++
                    if (gold in s.take(8)) acc.top8++
                    val ar = r.autoReplace
                    when {
                        ar == null -> acc.autoNone++
                        ar.lowercase() == gold -> acc.autoOk++
                        else -> acc.autoWrong++
                    }
                }
                if (r.autoReplace == null && gold !in r.suggestions.map { it.lowercase() } &&
                    missExamples.size < 25) missExamples.add("$typo -> want $gold  got ${r.suggestions.take(3)}  [$kind/$bucket]")
                // completion quality: half the word typed
                if (gold.length >= 5) {
                    val pre = gold.substring(0, (gold.length + 1) / 2)
                    val cr = engine.suggest(pre, 8, prev, prev2, deep = false)
                    val acc = completion.getOrPut(bucket) { Acc() }
                    acc.n++
                    val s = cr.suggestions.map { it.lowercase() }
                    if (gold in s.take(3)) acc.top3++
                    if (gold in s.take(8)) acc.top8++
                }
            }
            fun line(name: String, a: Acc) = "  %-26s n=%4d top1=%5.1f%% top3=%5.1f%% top8=%5.1f%% | auto: right=%5.1f%% WRONG=%4.1f%% none=%5.1f%%".format(
                name, a.n, 100.0 * a.top1 / a.n, 100.0 * a.top3 / a.n, 100.0 * a.top8 / a.n,
                100.0 * a.autoOk / a.n, 100.0 * a.autoWrong / a.n, 100.0 * a.autoNone / a.n)
            p("VALIDATOR OVER-ACCEPTANCE: typos that Hunspell calls VALID: $typoHsValid/$typoTotal = %.1f%% (and gold is in dict400k: $typoHsValidAndGoldInDict); typo itself in dict400k: $typoInDict/$typoTotal = %.1f%%".format(100.0 * typoHsValid / typoTotal, 100.0 * typoInDict / typoTotal))
            p("  examples of Hunspell-valid typos: " + falseValidExamples.joinToString(", "))
            p("EMPTY RESULTS although gold is in dict400k: $emptyN  -> hunspell-valid typo=$emptyValid, typo in dict top10k=$emptyDictTop, OTHER=$emptyOther")
            p("  OTHER examples: " + emptyOtherEx.joinToString(", "))
            p("TYPO CORRECTION by error class (n=$cases)")
            kinds.forEach { k -> byKind[k]?.let { p(line(k, it)) } }
            p("TYPO CORRECTION by where the intended word lives")
            byBucket.toSortedMap().forEach { (k, a) -> p(line(k, a)) }
            p("CONTROL: correct words wrongly auto-changed (false positives)")
            p("  overall: ${control.autoWrong}/${control.n} = %.2f%%".format(100.0 * control.autoWrong / control.n))
            ctrlBucket.toSortedMap().forEach { (k, a) ->
                p("  %-26s %d/%d = %.2f%%".format(k, a.autoWrong, a.n, 100.0 * a.autoWrong / a.n))
            }
            p("COMPLETION (first half of word typed, no typo)")
            completion.toSortedMap().forEach { (k, a) ->
                p("  %-26s n=%4d top3=%5.1f%% top8=%5.1f%%".format(k, a.n, 100.0 * a.top3 / a.n, 100.0 * a.top8 / a.n))
            }
            p("wrong auto-changes of CORRECT words (sample):")
            wrongExamples.forEach { p("   $it") }
            p("uncorrected typos (sample):")
            missExamples.forEach { p("   $it") }
            p()
        }
        System.getenv("PCK_BENCH_OUT")?.let { File(it).writeText(out.toString()) }
    }
}
