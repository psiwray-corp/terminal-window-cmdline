package com.psiwray.organization.terminal_window_cmdline.vt.input

/** A mouse button or wheel direction. */
public enum class MouseButton(
    internal val xtermCode: Int,
) {
    /** Primary/left button. */
    LEFT(0),

    /** Middle button. */
    MIDDLE(1),

    /** Secondary/right button. */
    RIGHT(2),

    /** Wheel scrolled up. */
    WHEEL_UP(64),

    /** Wheel scrolled down. */
    WHEEL_DOWN(65),
    ;

    /** True for wheel directions, which are press-only in terminal protocols. */
    internal val isWheel: Boolean
        get() = this == WHEEL_UP || this == WHEEL_DOWN
}
