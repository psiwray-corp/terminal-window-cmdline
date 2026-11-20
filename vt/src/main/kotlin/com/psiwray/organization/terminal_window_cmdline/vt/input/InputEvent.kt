package com.psiwray.organization.terminal_window_cmdline.vt.input

/** An immutable input event consumed by [InputEncoder]. */
public sealed interface InputEvent

/**
 * A keyboard event.
 *
 * Printable text normally arrives later as a [CharEvent]. A chord that the
 * encoder handles from this event suppresses that following character.
 *
 * @property key mapped physical key.
 * @property modifiers modifiers active for this event.
 * @property action press, repeat, or release.
 */
public data class KeyEvent(
    public val key: Key,
    public val modifiers: Modifiers = Modifiers.NONE,
    public val action: Action = Action.PRESS,
) : InputEvent

/**
 * A Unicode code point produced by the platform text-input system.
 *
 * @property codePoint Unicode scalar value.
 */
public data class CharEvent(
    public val codePoint: Int,
) : InputEvent {
    init {
        require(codePoint in 0..MAX_CODE_POINT && codePoint !in SURROGATE_RANGE) {
            "codePoint must be a Unicode scalar value: $codePoint"
        }
    }

    private companion object {
        private const val MAX_CODE_POINT: Int = 0x10FFFF
        private val SURROGATE_RANGE: IntRange = 0xD800..0xDFFF
    }
}

/**
 * A mouse button or pointer-motion event.
 *
 * [column] and [row] are zero-based cell coordinates. The encoder adds one
 * when producing a terminal report. For [Action.MOVE], [button] is the held
 * button when one is known and null for unbuttoned motion.
 *
 * @property column zero-based cell column.
 * @property row zero-based cell row.
 * @property button button involved, or null for unbuttoned motion.
 * @property action press, repeat, release, or move.
 * @property modifiers modifiers active for this event.
 */
public data class MouseEvent(
    public val column: Int,
    public val row: Int,
    public val button: MouseButton? = null,
    public val action: Action = Action.PRESS,
    public val modifiers: Modifiers = Modifiers.NONE,
) : InputEvent

/**
 * A scroll-wheel notch.
 *
 * Positive [delta] is up and negative is down; each unit is one report.
 * [column] and [row] are zero-based cell coordinates.
 *
 * @property column zero-based cell column.
 * @property row zero-based cell row.
 * @property delta signed wheel notches.
 * @property modifiers modifiers active for this event.
 */
public data class ScrollEvent(
    public val column: Int,
    public val row: Int,
    public val delta: Int,
    public val modifiers: Modifiers = Modifiers.NONE,
) : InputEvent

/**
 * Window focus changed.
 *
 * @property focused true when the window gained focus.
 */
public data class FocusEvent(
    public val focused: Boolean,
) : InputEvent

/**
 * Text was pasted into the window.
 *
 * @property text pasted text.
 */
public data class PasteEvent(
    public val text: String,
) : InputEvent
