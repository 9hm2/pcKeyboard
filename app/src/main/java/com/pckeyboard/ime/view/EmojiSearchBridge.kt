package com.pckeyboard.ime.view

/**
 * One-way channel from [EmojiSearchActivity] back into the IME service.
 * The activity drops the picked emoji in [pending] on tap + finish; the
 * service consumes it on the next onStartInputView and commits via
 * InputConnection. A timestamp lets stale entries (≥ 30 s) be ignored.
 */
object EmojiSearchBridge {

    @Volatile
    var pending: String? = null

    @Volatile
    var timestamp: Long = 0L

    fun consume(): String? {
        val text = pending ?: return null
        val ts = timestamp
        pending = null
        timestamp = 0L
        if (System.currentTimeMillis() - ts > 30_000L) return null
        return text
    }
}
