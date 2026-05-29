package com.pckeyboard.ime.clipboard

/**
 * One-way channel from [ClipboardEditorActivity] back into the IME service.
 * The editor activity sets [pending] on Send + finish; on the next
 * onStartInputView the service consumes and commits it, then clears.
 *
 * A timestamp lets the service ignore stale values (e.g. user opened the
 * editor, then cancelled and 10 minutes later started typing in a new
 * field — we don't want to randomly inject the abandoned text).
 */
object ClipboardEditorBridge {

    @Volatile
    var pending: Result? = null

    data class Result(
        val original: String,
        val edited: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** Returns the pending Result if it's still fresh (≤ 30 seconds old)
     *  and clears the slot. Stale or empty slots return null. */
    fun consume(): Result? {
        val current = pending ?: return null
        pending = null
        return if (System.currentTimeMillis() - current.timestamp <= 30_000L) current else null
    }
}
