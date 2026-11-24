package com.pckeyboard.ime.service

/**
 * Decides whether an input-session end is really "the screen just
 * rotated / the foldable just folded" — in which case a running voice
 * dictation must survive it.
 *
 * A display change makes Android restart the input session (the IME
 * view is rebuilt and onFinishInputView fires), which used to kill the
 * dictation every time. The configuration callback arrives just BEFORE
 * that restart, so "a display change was seen within [windowMs]" is the
 * test. Only genuine size/orientation changes count — locale, dark
 * mode or font-scale reconfigurations do not.
 *
 * Pure logic (time and values are passed in) so it's unit-testable.
 */
class DisplayChangeGrace(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var orientation = UNSET
    private var widthDp = UNSET
    private var heightDp = UNSET
    private var lastChangeAtMs = Long.MIN_VALUE

    /** Feed every onConfigurationChanged; returns true when this call
     *  was a genuine display change (and starts the grace window). */
    fun onConfiguration(orientation: Int, widthDp: Int, heightDp: Int, nowMs: Long): Boolean {
        val first = this.orientation == UNSET
        val changed = !first && (
            orientation != this.orientation ||
                widthDp != this.widthDp ||
                heightDp != this.heightDp
            )
        this.orientation = orientation
        this.widthDp = widthDp
        this.heightDp = heightDp
        if (changed) lastChangeAtMs = nowMs
        return changed
    }

    /** True while a recent display change should shield a dictation
     *  from the session restart it causes. */
    fun isRecent(nowMs: Long): Boolean =
        lastChangeAtMs != Long.MIN_VALUE && nowMs - lastChangeAtMs in 0..windowMs

    companion object {
        const val DEFAULT_WINDOW_MS = 2_500L
        private const val UNSET = Int.MIN_VALUE
    }
}
