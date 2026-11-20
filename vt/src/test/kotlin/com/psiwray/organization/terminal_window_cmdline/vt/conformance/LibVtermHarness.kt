package com.psiwray.organization.terminal_window_cmdline.vt.conformance

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserSink
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.fail

internal object LibVtermHarness {
    fun run(resource: String) {
        val text =
            LibVtermHarness::class.java
                .getResourceAsStream(resource)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: fail("missing resource $resource")

        val sink = RecordingSink()
        val parser = Parser(sink)
        var pendingPush: ByteArray? = null
        var expected = mutableListOf<String>()
        var section = ""
        var lineNumber = 0

        fun flushPush() {
            val push = pendingPush ?: return
            sink.events.clear()
            parser.write(push)
            val actual = sink.events.map { format(it) }
            assertEquals(expected, actual, "section '$section', line $lineNumber")
            pendingPush = null
            expected = mutableListOf()
        }

        for (rawLine in text.lineSequence()) {
            lineNumber++
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }
            when {
                line.startsWith("!") -> {
                    flushPush()
                    section = line.substring(1).trim()
                }

                line.startsWith("PUSH ") -> {
                    flushPush()
                    pendingPush = parseBytesLiteral(line.removePrefix("PUSH ").trim())
                }

                line == "INIT" || line == "RESET" -> {
                    flushPush()
                    parser.reset()
                    sink.events.clear()
                }

                line == "UTF8 0" || line == "WANTPARSER" -> {}

                line.startsWith("WANTSTATE") -> {}

                line.first().isLowerCase() -> {
                    expected += normalizeExpectation(line)
                }

                else -> {
                    fail("unknown test command at line $lineNumber: $line")
                }
            }
        }
        flushPush()
    }

    private fun parseBytesLiteral(literal: String): ByteArray {
        if (literal.length < 2 || literal.first() != '"' || literal.last() != '"') {
            fail("expected a quoted string: $literal")
        }
        val content = literal.substring(1, literal.length - 1)
        val output = ByteArrayOutputStream()
        var index = 0
        while (index < content.length) {
            val char = content[index]
            if (char != '\\') {
                output.write(char.toString().toByteArray(Charsets.UTF_8))
                index++
                continue
            }

            index++
            if (index >= content.length) {
                fail("trailing escape in string: $literal")
            }
            when (val escaped = content[index]) {
                'e' -> {
                    output.write(0x1B)
                    index++
                }

                'n' -> {
                    output.write(0x0A)
                    index++
                }

                'r' -> {
                    output.write(0x0D)
                    index++
                }

                't' -> {
                    output.write(0x09)
                    index++
                }

                '0' -> {
                    output.write(0x00)
                    index++
                }

                '\\' -> {
                    output.write(0x5C)
                    index++
                }

                '"' -> {
                    output.write('"'.code)
                    index++
                }

                '\'' -> {
                    output.write('\''.code)
                    index++
                }

                'x' -> {
                    index++
                    if (index < content.length && content[index] == '{') {
                        val close = content.indexOf('}', index)
                        if (close < 0) {
                            fail("unterminated \\x{} escape: $literal")
                        }
                        output.write(content.substring(index + 1, close).toInt(16))
                        index = close + 1
                    } else {
                        if (index + 2 > content.length) {
                            fail("short \\x escape: $literal")
                        }
                        output.write(content.substring(index, index + 2).toInt(16))
                        index += 2
                    }
                }

                else -> {
                    fail("unsupported escape \\$escaped in $literal")
                }
            }
        }
        return output.toByteArray()
    }

    private fun normalizeExpectation(line: String): String =
        when {
            line.startsWith("text ") -> {
                val values =
                    line
                        .removePrefix("text ")
                        .split(',')
                        .map { it.trim().removePrefix("0x").toInt(16) }
                "text " + values.joinToString(",") { it.toString(16) }
            }

            line.startsWith("control ") -> {
                val token = line.removePrefix("control ").trim()
                val value =
                    if (token.startsWith("0x")) {
                        token.removePrefix("0x").toInt(16)
                    } else {
                        token.toInt(10)
                    }
                "control " + value.toString(16).padStart(2, '0')
            }

            line.startsWith("escape ") -> {
                val bytes = parseBytesLiteral(line.removePrefix("escape ").trim())
                "escape " + bytes.toHex()
            }

            line.startsWith("csi ") -> {
                normalizeCsi(line.removePrefix("csi ").trim())
            }

            line.startsWith("osc ") -> {
                normalizeOsc(line.removePrefix("osc ").trim())
            }

            line.startsWith("dcs ") -> {
                normalizeStringEvent("dcs", line.removePrefix("dcs ").trim())
            }

            line.startsWith("apc ") -> {
                normalizeStringEvent("apc", line.removePrefix("apc ").trim())
            }

            line.startsWith("pm ") -> {
                normalizeStringEvent("pm", line.removePrefix("pm ").trim())
            }

            line.startsWith("sos ") -> {
                normalizeStringEvent("sos", line.removePrefix("sos ").trim())
            }

            else -> {
                fail("unknown expectation: $line")
            }
        }

    private fun normalizeCsi(rest: String): String {
        val finalToken = rest.substringBefore(' ')
        val finalByte = finalToken.removePrefix("0x").toInt(16)
        val remainder = rest.substringAfter(' ', "").trim()
        val builder = StringBuilder("csi ")
        builder.append(finalByte.toString(16).padStart(2, '0'))
        if (remainder.isEmpty()) {
            builder.append(" *")
        } else {
            builder.append(' ').append(remainder)
        }
        return builder.toString()
    }

    private fun normalizeOsc(rest: String): String {
        var cursor = rest
        var command: String? = null
        if (cursor.startsWith("[")) {
            cursor = cursor.substring(1)
            val end = cursor.indexOfFirst { it == ' ' || it == '"' || it == ']' }
            val digits = if (end < 0) cursor else cursor.substring(0, end)
            if (digits.isNotEmpty()) {
                command = digits
                cursor = cursor.substring(end)
            } else if (end >= 0) {
                cursor = cursor.substring(end)
            } else {
                cursor = ""
            }
            cursor = cursor.trimStart()
        }

        var final = false
        if (cursor.endsWith("]")) {
            final = true
            cursor = cursor.dropLast(1).trimEnd()
        }
        val payload = if (cursor.isEmpty()) ByteArray(0) else parseBytesLiteral(cursor)
        return buildString {
            append("osc ")
            if (command != null) {
                append('[').append(command).append(';')
            }
            append(payload.toHex())
            if (final) {
                append(']')
            }
        }
    }

    private fun normalizeStringEvent(
        kind: String,
        rest: String,
    ): String {
        var cursor = rest
        var initial = false
        if (cursor.startsWith("[")) {
            initial = true
            cursor = cursor.substring(1)
        }
        var final = false
        if (cursor.endsWith("]")) {
            final = true
            cursor = cursor.dropLast(1).trimEnd()
        }
        val payload = if (cursor.isEmpty()) ByteArray(0) else parseBytesLiteral(cursor)
        return buildString {
            append(kind).append(' ')
            if (initial) {
                append('[')
            }
            append(payload.toHex())
            if (final) {
                append(']')
            }
        }
    }

    private fun format(event: ParserEvent): String =
        when (event) {
            is ParserEvent.Print -> {
                "text " + event.bytes.toHexList()
            }

            is ParserEvent.Execute -> {
                "control " + event.control.toString(16).padStart(2, '0')
            }

            is ParserEvent.Esc -> {
                "escape " + event.intermediates.toHex() +
                    event.finalByte.toString(16).padStart(2, '0')
            }

            is ParserEvent.Csi -> {
                buildString {
                    append("csi ").append(event.finalByte.toString(16).padStart(2, '0'))
                    if (event.privateMarker != 0) {
                        append(" L=").append(event.privateMarker.toString(16).padStart(2, '0'))
                    }
                    if (event.parameters.isEmpty()) {
                        append(" *")
                    } else {
                        event.parameters.forEachIndexed { index, value ->
                            append(if (index == 0) ' ' else ',')
                            if (value == ParserEvent.MISSING_PARAMETER) {
                                append('*')
                            } else {
                                append(value)
                            }
                            if (event.parameterMore[index]) {
                                append('+')
                            }
                        }
                    }
                    if (event.intermediates.isNotEmpty()) {
                        append(" I=").append(event.intermediates.toHex())
                    }
                }
            }

            is ParserEvent.Osc -> {
                buildString {
                    append("osc ")
                    if (event.initial) {
                        append('[')
                        if (event.command != ParserEvent.NO_COMMAND) {
                            append(event.command).append(';')
                        }
                    }
                    append(event.bytes.toHex())
                    if (event.final) {
                        append(']')
                    }
                }
            }

            is ParserEvent.Dcs -> {
                buildString {
                    append("dcs ")
                    if (event.initial) {
                        append('[')
                        if (event.privateMarker != 0) {
                            append(event.privateMarker.toString(16).padStart(2, '0'))
                        }
                        event.parameters.forEachIndexed { index, value ->
                            if (index > 0) {
                                append(';')
                            }
                            if (value != ParserEvent.MISSING_PARAMETER) {
                                append(value)
                            }
                            if (event.parameterMore[index]) {
                                append(':')
                            }
                        }
                        append(event.intermediates.toHex())
                        append(event.finalByte.toString(16).padStart(2, '0'))
                    }
                    append(event.bytes.toHex())
                    if (event.final) {
                        append(']')
                    }
                }
            }

            is ParserEvent.Apc -> {
                formatSimple("apc", event.bytes, event.initial, event.final)
            }

            is ParserEvent.Pm -> {
                formatSimple("pm", event.bytes, event.initial, event.final)
            }

            is ParserEvent.Sos -> {
                formatSimple("sos", event.bytes, event.initial, event.final)
            }
        }

    private fun formatSimple(
        kind: String,
        bytes: ByteArray,
        initial: Boolean,
        final: Boolean,
    ): String =
        buildString {
            append(kind).append(' ')
            if (initial) {
                append('[')
            }
            append(bytes.toHex())
            if (final) {
                append(']')
            }
        }

    private class RecordingSink : ParserSink {
        val events: MutableList<ParserEvent> = mutableListOf()

        override fun onEvent(event: ParserEvent) {
            events += event
        }
    }
}

private fun ByteArray.toHex(): String =
    buildString(size * 2) {
        for (byte in this@toHex) {
            append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
        }
    }

private fun ByteArray.toHexList(): String = joinToString(",") { (it.toInt() and 0xFF).toString(16) }
