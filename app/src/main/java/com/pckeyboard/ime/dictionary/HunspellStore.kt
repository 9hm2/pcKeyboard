package com.pckeyboard.ime.dictionary

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.apache.lucene.analysis.hunspell.Dictionary as HunspellDictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import java.util.concurrent.Executors

/**
 * Process-wide cache of loaded [Hunspell] checkers (the LibreOffice
 * spell-check engine, Lucene's pure-Java rewrite), one per language.
 *
 * Hunspell complements the frequency dictionary: it validates words
 * *morphologically* from stems + affix rules, so rare inflections and
 * Hungarian compounds the corpus never saw still count as correct —
 * which stops the auto-correcter from "fixing" them.
 *
 * Loading parses the .aff/.dic pair and builds FSTs (Hungarian is the
 * heavyweight — a second or two), so it happens on a background thread;
 * until it flips in, the suggestion engine simply runs without a
 * validator, exactly as before. Any failure (odd dictionary, missing
 * JDK API on old devices, OOM) marks the language as failed and the
 * keyboard keeps working without Hunspell.
 *
 * Thread-safety: [peek]/[preload]/[validatorFor] are main-thread only;
 * the returned checker is used from the main thread exclusively.
 */
object HunspellStore {

    private const val MAX_LOADED = 2

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hunspell-loader").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val loaded = object : LinkedHashMap<String, Hunspell>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Hunspell>) =
            size > MAX_LOADED
    }
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    fun peek(langId: String): Hunspell? = loaded[langId]

    /** A `word -> looks valid` lambda for the engine, or null while the
     *  checker isn't loaded. Never throws — a checker hiccup on some
     *  exotic input counts the word as valid (fail-open: an uncertain
     *  validator must not cause corrections). */
    fun validatorFor(langId: String): ((String) -> Boolean)? {
        val checker = loaded[langId] ?: return null
        return { word ->
            try {
                checker.spell(word)
            } catch (_: Throwable) {
                true
            }
        }
    }

    /** Ensures the checker for [langId] is loaded or loading. Languages
     *  that failed once are not retried for the life of the process. */
    fun preload(context: Context, langId: String) {
        if (langId in loaded || langId in loading || langId in failed) return
        loading.add(langId)
        val appContext = context.applicationContext
        executor.execute {
            val checker = try {
                val assets = appContext.assets
                assets.open("hunspell/$langId.aff").use { aff ->
                    assets.open("hunspell/$langId.dic").use { dic ->
                        val dictionary = HunspellDictionary(
                            ByteBuffersDirectory(), "hunspell_$langId", aff, dic
                        )
                        Hunspell(dictionary)
                    }
                }
            } catch (_: Throwable) {
                null
            }
            mainHandler.post {
                loading.remove(langId)
                if (checker == null) {
                    failed.add(langId)
                } else {
                    loaded[langId] = checker
                }
            }
        }
    }
}
