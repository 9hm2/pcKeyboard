package com.pckeyboard.ime.dictionary

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Process-wide cache of loaded [TrigramModel]s — same background-load /
 * LRU / fail-open pattern as the other model stores.
 */
object TrigramStore {

    private const val MAX_LOADED = 2

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "trigram-loader").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val loaded = object : LinkedHashMap<String, TrigramModel>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrigramModel>) =
            size > MAX_LOADED
    }
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    fun peek(langId: String): TrigramModel? = loaded[langId]

    fun preload(context: Context, langId: String) {
        if (langId in loaded || langId in loading || langId in failed) return
        loading.add(langId)
        val appContext = context.applicationContext
        executor.execute {
            val model = TrigramModel.load(appContext, langId)
            mainHandler.post {
                loading.remove(langId)
                if (model == null) failed.add(langId) else loaded[langId] = model
            }
        }
    }
}
