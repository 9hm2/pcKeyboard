package com.pckeyboard.ime.dictionary

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Process-wide cache of loaded [WordDictionary]s, one per language.
 *
 * Loading parses ~100k+ lines and builds the lookup structures, which
 * takes a noticeable moment — so it happens on a single background
 * thread and the keyboard simply types without suggestions until the
 * dictionary flips in. Only the most recently used [MAX_LOADED]
 * languages stay in memory.
 */
object DictionaryStore {

    private const val MAX_LOADED = 2

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dict-loader").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** LRU: most recently used last. Access-ordered by [peek]/insert. */
    private val loaded = object : LinkedHashMap<String, WordDictionary>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordDictionary>) =
            size > MAX_LOADED
    }
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    /** The dictionary for [langId] if it's already in memory, else null
     *  (no load is triggered — use [preload] for that). Main thread only. */
    fun peek(langId: String): WordDictionary? = loaded[langId]

    /**
     * Ensures the dictionary for [langId] is loaded or loading. Runs
     * [onLoaded] on the main thread once available (immediately when
     * already cached). Languages that failed to load once are not
     * retried for the life of the process. Main thread only.
     */
    fun preload(context: Context, langId: String, onLoaded: (WordDictionary) -> Unit = {}) {
        loaded[langId]?.let { onLoaded(it); return }
        if (langId in loading || langId in failed) return
        loading.add(langId)
        val appContext = context.applicationContext
        executor.execute {
            val dict = WordDictionary.load(appContext, langId)
            mainHandler.post {
                loading.remove(langId)
                if (dict == null) {
                    failed.add(langId)
                } else {
                    loaded[langId] = dict
                    onLoaded(dict)
                }
            }
        }
    }
}
