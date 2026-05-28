package com.pckeyboard.ime.model

/**
 * Represents a single key on the keyboard.
 * Mirrors the structure used by full PC-style layouts so we can express
 * every modifier (Ctrl, Alt, Meta, Shift) plus normal characters.
 */
data class Key(
    val code: Int,
    val label: String,
    val shiftLabel: String? = null,
    val popupChars: String? = null,
    val widthWeight: Float = 1f,
    val type: KeyType = KeyType.CHAR,
    val keyCode: Int = 0,
    val repeatable: Boolean = false,
    val sticky: Boolean = false,
    val icon: String? = null
) {
    companion object {
        const val CODE_UNSPECIFIED = 0

        fun char(label: String, shift: String? = null, popup: String? = null, weight: Float = 1f): Key =
            Key(
                code = label.codePointAt(0),
                label = label,
                shiftLabel = shift,
                popupChars = popup,
                widthWeight = weight,
                type = KeyType.CHAR
            )

        fun letter(lower: String, popup: String? = null, weight: Float = 1f): Key =
            Key(
                code = lower.codePointAt(0),
                label = lower,
                shiftLabel = lower.uppercase(),
                popupChars = popup,
                widthWeight = weight,
                type = KeyType.LETTER
            )

        fun fn(label: String, type: KeyType, keyCode: Int = 0, weight: Float = 1f,
               sticky: Boolean = false, repeatable: Boolean = false, icon: String? = null): Key =
            Key(
                code = CODE_UNSPECIFIED,
                label = label,
                widthWeight = weight,
                type = type,
                keyCode = keyCode,
                sticky = sticky,
                repeatable = repeatable,
                icon = icon
            )
    }
}

enum class KeyType {
    CHAR,
    LETTER,
    SHIFT,
    CTRL,
    ALT,
    META,
    FN,
    SPACE,
    ENTER,
    BACKSPACE,
    DELETE,
    TAB,
    ESC,
    CAPS_LOCK,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    INSERT,
    LAYOUT_SWITCH,
    SYMBOL_SWITCH,
    LANGUAGE_SWITCH,
    EMOJI,
    SETTINGS,
    HIDE
}
