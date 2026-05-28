package com.pckeyboard.ime.model

import android.view.KeyEvent

/**
 * Tracks current state of modifier keys (Shift, Ctrl, Alt, Meta, Fn).
 *
 * Each modifier has three logical states:
 *  - OFF: not active
 *  - ONCE: active for the next key press, then released
 *  - LOCKED: stays on until tapped again (Caps Lock style)
 */
class ModifierState {

    enum class State { OFF, ONCE, LOCKED }

    var shift: State = State.OFF
        private set
    var ctrl: State = State.OFF
        private set
    var alt: State = State.OFF
        private set
    var meta: State = State.OFF
        private set
    var fn: State = State.OFF
        private set
    var capsLock: Boolean = false
        private set

    fun tapShift() {
        shift = when (shift) {
            State.OFF -> State.ONCE
            State.ONCE -> State.LOCKED
            State.LOCKED -> State.OFF
        }
    }

    fun tapCtrl() { ctrl = cycle(ctrl) }
    fun tapAlt() { alt = cycle(alt) }
    fun tapMeta() { meta = cycle(meta) }
    fun tapFn() { fn = cycle(fn) }

    fun toggleCapsLock() {
        capsLock = !capsLock
        shift = if (capsLock) State.LOCKED else State.OFF
    }

    /** Consumes the ONCE modifiers after a character key is pressed. */
    fun consumeAfterChar() {
        if (shift == State.ONCE) shift = State.OFF
        if (ctrl == State.ONCE) ctrl = State.OFF
        if (alt == State.ONCE) alt = State.OFF
        if (meta == State.ONCE) meta = State.OFF
        if (fn == State.ONCE) fn = State.OFF
    }

    fun isShiftActive(): Boolean = shift != State.OFF || capsLock
    fun isCtrlActive(): Boolean = ctrl != State.OFF
    fun isAltActive(): Boolean = alt != State.OFF
    fun isMetaActive(): Boolean = meta != State.OFF
    fun isFnActive(): Boolean = fn != State.OFF

    /** Returns Android KeyEvent meta flags for the active modifiers. */
    fun toMetaState(): Int {
        var meta = 0
        if (isShiftActive()) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (isCtrlActive())  meta = meta or KeyEvent.META_CTRL_ON  or KeyEvent.META_CTRL_LEFT_ON
        if (isAltActive())   meta = meta or KeyEvent.META_ALT_ON   or KeyEvent.META_ALT_LEFT_ON
        if (isMetaActive())  meta = meta or KeyEvent.META_META_ON  or KeyEvent.META_META_LEFT_ON
        return meta
    }

    fun reset() {
        shift = State.OFF; ctrl = State.OFF; alt = State.OFF
        meta = State.OFF; fn = State.OFF; capsLock = false
    }

    /** Returns true when any non-shift modifier is held (so taps should send key events). */
    fun shouldSendAsKeyEvent(): Boolean =
        isCtrlActive() || isAltActive() || isMetaActive() || isFnActive()

    private fun cycle(s: State): State = when (s) {
        State.OFF -> State.ONCE
        State.ONCE -> State.LOCKED
        State.LOCKED -> State.OFF
    }
}
