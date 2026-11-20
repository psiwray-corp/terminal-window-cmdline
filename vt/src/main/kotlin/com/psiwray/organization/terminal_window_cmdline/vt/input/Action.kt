package com.psiwray.organization.terminal_window_cmdline.vt.input

/** The action carried by a key or mouse event. */
public enum class Action {
    /** Key or button went down. */
    PRESS,

    /** Key repeated while held, or a repeat button event. */
    REPEAT,

    /** Key or button went up. */
    RELEASE,

    /** Pointer moved without a new button action. */
    MOVE,
}
