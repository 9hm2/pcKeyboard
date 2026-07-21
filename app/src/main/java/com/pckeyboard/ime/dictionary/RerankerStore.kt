package com.pckeyboard.ime.dictionary

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Process-wide cache of loaded [NeuralReranker]s. Same background-load /
 * fail-open pattern as the other stores, but only ONE model stays in
 * memory (they're the heaviest layer) and absence is the normal state —
 * the whole feature is dormant until model files ship in
 * assets/reranker/.
 */
object RerankerStore {

    private const val MAX_LOADED = 1

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "reranker-loader").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val loaded = object : LinkedHashMap<String, NeuralReranker>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NeuralReranker>): Boolean {
            val evict = size > MAX_LOADED
            if (evict) eldest.value.close()
            return evict
        }
    }
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    /** Scoring lambda for the engine, or null while unavailable. */
    fun scorerFor(langId: String): ((String, String) -> Double?)? {
        val model = loaded[langId] ?: return null
        return { context, candidate -> model.scoreCandidate(context, candidate) }
    }

    fun preload(context: Context, langId: String) {
        if (langId in loaded || langId in loading || langId in failed) return
        loading.add(langId)
        val appContext = context.applicationContext
        executor.execute {
            val model = NeuralReranker.load(appContext, langId)
            mainHandler.post {
                loading.remove(langId)
                if (model == null) failed.add(langId) else loaded[langId] = model
            }
        }
    }
}
