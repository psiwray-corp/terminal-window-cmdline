package com.psiwray.organization.terminal_window_cmdline.vt.input

/**
 * A physical keyboard key.
 *
 * Names follow the usual PC keyboard layout. [UNKNOWN] is used when the
 * window system cannot identify a real key token.
 */
public enum class Key {
    UNKNOWN,
    SPACE,
    APOSTROPHE,
    COMMA,
    MINUS,
    PERIOD,
    SLASH,
    NUM_0,
    NUM_1,
    NUM_2,
    NUM_3,
    NUM_4,
    NUM_5,
    NUM_6,
    NUM_7,
    NUM_8,
    NUM_9,
    SEMICOLON,
    EQUAL,
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P,
    Q,
    R,
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    LEFT_BRACKET,
    BACKSLASH,
    RIGHT_BRACKET,
    GRAVE_ACCENT,
    ESCAPE,
    ENTER,
    TAB,
    BACKSPACE,
    INSERT,
    DELETE,
    RIGHT,
    LEFT,
    DOWN,
    UP,
    PAGE_UP,
    PAGE_DOWN,
    HOME,
    END,
    CAPS_LOCK,
    SCROLL_LOCK,
    NUM_LOCK,
    PRINT_SCREEN,
    PAUSE,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    KP_0,
    KP_1,
    KP_2,
    KP_3,
    KP_4,
    KP_5,
    KP_6,
    KP_7,
    KP_8,
    KP_9,
    KP_DECIMAL,
    KP_DIVIDE,
    KP_MULTIPLY,
    KP_SUBTRACT,
    KP_ADD,
    KP_ENTER,
    KP_EQUAL,
    LEFT_SHIFT,
    LEFT_CONTROL,
    LEFT_ALT,
    LEFT_SUPER,
    RIGHT_SHIFT,
    RIGHT_CONTROL,
    RIGHT_ALT,
    RIGHT_SUPER,
    MENU,
    ;

    /** True for keys that normally produce text through the platform input method. */
    internal val isPrintable: Boolean
        get() =
            when (this) {
                SPACE,
                APOSTROPHE,
                COMMA,
                MINUS,
                PERIOD,
                SLASH,
                NUM_0,
                NUM_1,
                NUM_2,
                NUM_3,
                NUM_4,
                NUM_5,
                NUM_6,
                NUM_7,
                NUM_8,
                NUM_9,
                SEMICOLON,
                EQUAL,
                A,
                B,
                C,
                D,
                E,
                F,
                G,
                H,
                I,
                J,
                K,
                L,
                M,
                N,
                O,
                P,
                Q,
                R,
                S,
                T,
                U,
                V,
                W,
                X,
                Y,
                Z,
                LEFT_BRACKET,
                BACKSLASH,
                RIGHT_BRACKET,
                GRAVE_ACCENT,
                -> true

                else -> false
            }

    /** Unshifted Unicode code point for a text-producing key, or null. */
    internal fun unshiftedCodePoint(): Int? =
        when (this) {
            SPACE -> 0x20
            APOSTROPHE -> 0x27
            COMMA -> 0x2C
            MINUS -> 0x2D
            PERIOD -> 0x2E
            SLASH -> 0x2F
            NUM_0 -> 0x30
            NUM_1 -> 0x31
            NUM_2 -> 0x32
            NUM_3 -> 0x33
            NUM_4 -> 0x34
            NUM_5 -> 0x35
            NUM_6 -> 0x36
            NUM_7 -> 0x37
            NUM_8 -> 0x38
            NUM_9 -> 0x39
            SEMICOLON -> 0x3B
            EQUAL -> 0x3D
            A -> 0x61
            B -> 0x62
            C -> 0x63
            D -> 0x64
            E -> 0x65
            F -> 0x66
            G -> 0x67
            H -> 0x68
            I -> 0x69
            J -> 0x6A
            K -> 0x6B
            L -> 0x6C
            M -> 0x6D
            N -> 0x6E
            O -> 0x6F
            P -> 0x70
            Q -> 0x71
            R -> 0x72
            S -> 0x73
            T -> 0x74
            U -> 0x75
            V -> 0x76
            W -> 0x77
            X -> 0x78
            Y -> 0x79
            Z -> 0x7A
            LEFT_BRACKET -> 0x5B
            BACKSLASH -> 0x5C
            RIGHT_BRACKET -> 0x5D
            GRAVE_ACCENT -> 0x60
            ESCAPE -> 0x1B
            ENTER -> 0x0D
            TAB -> 0x09
            BACKSPACE -> 0x7F
            else -> null
        }

    /** Shifted Unicode code point for a text-producing key, or null. */
    internal fun shiftedCodePoint(): Int? =
        when (this) {
            SPACE -> 0x20
            APOSTROPHE -> 0x22
            COMMA -> 0x3C
            MINUS -> 0x5F
            PERIOD -> 0x3E
            SLASH -> 0x3F
            NUM_0 -> 0x29
            NUM_1 -> 0x21
            NUM_2 -> 0x40
            NUM_3 -> 0x23
            NUM_4 -> 0x24
            NUM_5 -> 0x25
            NUM_6 -> 0x5E
            NUM_7 -> 0x26
            NUM_8 -> 0x2A
            NUM_9 -> 0x28
            SEMICOLON -> 0x3A
            EQUAL -> 0x2B
            A -> 0x41
            B -> 0x42
            C -> 0x43
            D -> 0x44
            E -> 0x45
            F -> 0x46
            G -> 0x47
            H -> 0x48
            I -> 0x49
            J -> 0x4A
            K -> 0x4B
            L -> 0x4C
            M -> 0x4D
            N -> 0x4E
            O -> 0x4F
            P -> 0x50
            Q -> 0x51
            R -> 0x52
            S -> 0x53
            T -> 0x54
            U -> 0x55
            V -> 0x56
            W -> 0x57
            X -> 0x58
            Y -> 0x59
            Z -> 0x5A
            LEFT_BRACKET -> 0x7B
            BACKSLASH -> 0x7C
            RIGHT_BRACKET -> 0x7D
            GRAVE_ACCENT -> 0x7E
            else -> null
        }
}
