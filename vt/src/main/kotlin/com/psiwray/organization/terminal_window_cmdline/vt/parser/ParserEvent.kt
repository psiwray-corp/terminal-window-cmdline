package com.psiwray.organization.terminal_window_cmdline.vt.parser

/**
 * One event produced by [Parser].
 *
 * Events are emitted synchronously from [Parser.write]. Byte arrays are
 * snapshots owned by the event; the parser never reuses them.
 */
public sealed interface ParserEvent {
    /** A run of printable bytes. UTF-8 decoding belongs to the consumer. */
    public data class Print(
        /** Printable bytes in input order. */
        public val bytes: ByteArray,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean = other is Print && bytes.contentEquals(other.bytes)

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** A C0 or C1 control byte. */
    public data class Execute(
        /** Control byte value, `0x00` to `0xFF`. */
        public val control: Int,
    ) : ParserEvent

    /** An escape sequence dispatch. */
    public data class Esc(
        /** Collected intermediate bytes, `0x20` to `0x2F`, in order. */
        public val intermediates: ByteArray,
        /** Final byte, `0x30` to `0x7E`. */
        public val finalByte: Int,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Esc &&
                intermediates.contentEquals(other.intermediates) &&
                finalByte == other.finalByte

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int = 31 * intermediates.contentHashCode() + finalByte
    }

    /** A control sequence dispatch. */
    public data class Csi(
        /** Private marker `?`, `>`, `=`, or `<`; zero when absent. */
        public val privateMarker: Int,
        /**
         * Parameter values in input order. A missing value is
         * [ParserEvent.MISSING_PARAMETER]. A value followed by a colon has the
         * corresponding [parameterMore] entry set.
         */
        public val parameters: IntArray,
        /** True when the parameter at the same index is followed by `:`. */
        public val parameterMore: BooleanArray,
        /** Collected intermediate bytes, `0x20` to `0x2F`, in order. */
        public val intermediates: ByteArray,
        /** Final byte, `0x40` to `0x7E`. */
        public val finalByte: Int,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Csi &&
                privateMarker == other.privateMarker &&
                parameters.contentEquals(other.parameters) &&
                parameterMore.contentEquals(other.parameterMore) &&
                intermediates.contentEquals(other.intermediates) &&
                finalByte == other.finalByte

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = privateMarker
            result = 31 * result + parameters.contentHashCode()
            result = 31 * result + parameterMore.contentHashCode()
            result = 31 * result + intermediates.contentHashCode()
            return 31 * result + finalByte
        }
    }

    /** A chunk of an operating-system command string. */
    public data class Osc(
        /** Numeric command before `;`, or [ParserEvent.NO_COMMAND]. */
        public val command: Int,
        /** Payload bytes after the numeric command and `;`. */
        public val bytes: ByteArray,
        /** True for the first chunk of this string. */
        public val initial: Boolean,
        /** True when this chunk ends the string. */
        public val final: Boolean,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Osc &&
                command == other.command &&
                bytes.contentEquals(other.bytes) &&
                initial == other.initial &&
                final == other.final

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = command
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + initial.hashCode()
            return 31 * result + final.hashCode()
        }
    }

    /** A chunk of a device-control string. */
    public data class Dcs(
        /** Private marker `?`, `>`, `=`, or `<`; zero when absent. */
        public val privateMarker: Int,
        /** Parameter values from the DCS introducer. */
        public val parameters: IntArray,
        /** True when the parameter at the same index is followed by `:`. */
        public val parameterMore: BooleanArray,
        /** Intermediate bytes from the DCS introducer. */
        public val intermediates: ByteArray,
        /** Final byte that opened passthrough. */
        public val finalByte: Int,
        /** Passthrough payload bytes. */
        public val bytes: ByteArray,
        /** True for the first chunk of this string. */
        public val initial: Boolean,
        /** True when this chunk ends the string. */
        public val final: Boolean,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Dcs &&
                privateMarker == other.privateMarker &&
                parameters.contentEquals(other.parameters) &&
                parameterMore.contentEquals(other.parameterMore) &&
                intermediates.contentEquals(other.intermediates) &&
                finalByte == other.finalByte &&
                bytes.contentEquals(other.bytes) &&
                initial == other.initial &&
                final == other.final

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = privateMarker
            result = 31 * result + parameters.contentHashCode()
            result = 31 * result + parameterMore.contentHashCode()
            result = 31 * result + intermediates.contentHashCode()
            result = 31 * result + finalByte
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + initial.hashCode()
            return 31 * result + final.hashCode()
        }
    }

    /** A chunk of an application-program command string. */
    public data class Apc(
        /** Payload bytes. */
        public val bytes: ByteArray,
        /** True for the first chunk of this string. */
        public val initial: Boolean,
        /** True when this chunk ends the string. */
        public val final: Boolean,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Apc &&
                bytes.contentEquals(other.bytes) &&
                initial == other.initial &&
                final == other.final

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + initial.hashCode()
            return 31 * result + final.hashCode()
        }
    }

    /** A chunk of a privacy-message string. */
    public data class Pm(
        /** Payload bytes. */
        public val bytes: ByteArray,
        /** True for the first chunk of this string. */
        public val initial: Boolean,
        /** True when this chunk ends the string. */
        public val final: Boolean,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Pm &&
                bytes.contentEquals(other.bytes) &&
                initial == other.initial &&
                final == other.final

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + initial.hashCode()
            return 31 * result + final.hashCode()
        }
    }

    /** A chunk of a start-of-string control string. */
    public data class Sos(
        /** Payload bytes. */
        public val bytes: ByteArray,
        /** True for the first chunk of this string. */
        public val initial: Boolean,
        /** True when this chunk ends the string. */
        public val final: Boolean,
    ) : ParserEvent {
        /** Compares event fields by value, including array contents. */
        override fun equals(other: Any?): Boolean =
            other is Sos &&
                bytes.contentEquals(other.bytes) &&
                initial == other.initial &&
                final == other.final

        /** Hashes event fields by value, including array contents. */
        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + initial.hashCode()
            return 31 * result + final.hashCode()
        }
    }

    /** Shared parameter sentinels. */
    public companion object {
        /** Value used for an omitted CSI/DCS parameter. */
        public const val MISSING_PARAMETER: Int = -1

        /** Command value used when an OSC string has no numeric command. */
        public const val NO_COMMAND: Int = -1
    }
}

/** Receives [Parser] events synchronously. */
public fun interface ParserSink {
    /** Handles one parser event. */
    public fun onEvent(event: ParserEvent)
}
