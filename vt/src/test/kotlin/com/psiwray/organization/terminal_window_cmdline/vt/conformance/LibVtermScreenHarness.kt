package com.psiwray.organization.terminal_window_cmdline.vt.conformance

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.screen.CellFlags
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

internal object LibVtermScreenHarness {
    fun run(resource: String) {
        val text =
            LibVtermScreenHarness::class.java
                .getResourceAsStream(resource)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: fail("missing resource $resource")

        val state = TerminalState(25, 80)
        val parser = Parser(state)
        var section = ""
        var lineNumber = 0
        var skipAll = false

        for (rawLine in text.lineSequence()) {
            lineNumber++
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }
            if (line.startsWith("!")) {
                section = line.substring(1).trim()
                continue
            }
            if (skipAll) {
                continue
            }
            when {
                line == "UNSUPPORTED" -> {
                    skipAll = true
                }

                line == "INIT" || line == "UTF8 0" || line == "UTF8 1" || line == "WANTPARSER" -> {}

                line.startsWith("WANTSTATE") || line.startsWith("WANTSCREEN") -> {}

                line == "RESET" -> {
                    parser.reset()
                    state.reset()
                }

                line.startsWith("RESIZE ") -> {
                    val parts = line.removePrefix("RESIZE ").split(',')
                    if (parts.size != 2) {
                        fail("bad RESIZE at line $lineNumber: $line")
                    }
                    state.resize(parts[0].trim().toInt(), parts[1].trim().toInt())
                }

                line.startsWith("PUSH ") -> {
                    parser.write(parseBytesExpression(line.removePrefix("PUSH ").trim()))
                }

                line.startsWith("putglyph ") -> {
                    checkPutGlyph(state, line.removePrefix("putglyph ").trim(), section, lineNumber)
                }

                line.startsWith("?cursor =") -> {
                    val values = line.removePrefix("?cursor =").trim().split(',')
                    if (values.size != 2) {
                        fail("bad ?cursor at line $lineNumber: $line")
                    }
                    assertEquals(
                        values[0].trim().toInt(),
                        state.cursorRow,
                        "cursor row, section '$section', line $lineNumber",
                    )
                    assertEquals(
                        values[1].trim().toInt(),
                        state.cursorColumn,
                        "cursor column, section '$section', line $lineNumber",
                    )
                }

                line.startsWith("?cell ") -> {
                    checkCell(state, line.removePrefix("?cell ").trim(), section, lineNumber)
                }

                line.startsWith("?row ") -> {
                    checkRow(state, line.removePrefix("?row ").trim(), section, lineNumber)
                }

                line.first().isLowerCase() -> {
                    fail("unsupported screen expectation at line $lineNumber: $line")
                }

                else -> {
                    fail("unknown screen test command at line $lineNumber: $line")
                }
            }
        }
    }

    private fun checkPutGlyph(
        state: TerminalState,
        body: String,
        section: String,
        lineNumber: Int,
    ) {
        val tokens = body.split(' ')
        if (tokens.size < 3) {
            fail("bad putglyph at line $lineNumber: $body")
        }
        val codePoints = tokens[0].split(',').map { it.removePrefix("0x").toInt(16) }
        val width = tokens[1].toInt()
        val position = tokens[2].split(',')
        if (position.size != 2) {
            fail("bad putglyph position at line $lineNumber: $body")
        }
        val row = position[0].toInt()
        val column = position[1].toInt()
        val context = "section '$section', line $lineNumber, cell $row,$column"

        assertEquals(codePoints[0], state.screen.codePointAt(row, column), "base code point, $context")
        assertEquals(width, state.screen.widthAt(row, column), "width, $context")
        assertEquals(codePoints.size - 1, state.screen.combiningCountAt(row, column), "combining count, $context")
        for (index in 1 until codePoints.size) {
            assertEquals(
                codePoints[index],
                state.screen.combiningAt(row, column, index - 1),
                "combining[$index], $context",
            )
        }
        if (width == 2) {
            assertTrue(
                state.screen.flagsAt(row, column) and CellFlags.WIDE_CHAR != 0,
                "wide flag, $context",
            )
            assertTrue(
                state.screen.flagsAt(row, column + 1) and CellFlags.WIDE_CHAR_SPACER != 0,
                "wide spacer, $context",
            )
        }
    }

    private fun checkCell(
        state: TerminalState,
        body: String,
        section: String,
        lineNumber: Int,
    ) {
        val equals = body.indexOf('=')
        if (equals < 0) {
            fail("bad ?cell at line $lineNumber: $body")
        }
        val position = body.substring(0, equals).trim().split(',')
        val expected =
            body
                .substring(equals + 1)
                .trim()
                .removePrefix("0x")
                .toInt(16)
        if (position.size != 2) {
            fail("bad ?cell position at line $lineNumber: $body")
        }
        assertEquals(
            expected,
            state.screen.codePointAt(position[0].trim().toInt(), position[1].trim().toInt()),
            "cell, section '$section', line $lineNumber",
        )
    }

    private fun checkRow(
        state: TerminalState,
        body: String,
        section: String,
        lineNumber: Int,
    ) {
        val equals = body.indexOf('=')
        if (equals < 0) {
            fail("bad ?row at line $lineNumber: $body")
        }
        val row = body.substring(0, equals).trim().toInt()
        val expected = String(parseBytesLiteral(body.substring(equals + 1).trim()), Charsets.UTF_8)
        val expectedCodePoints = expected.codePoints().toArray()
        var column = 0
        for (codePoint in expectedCodePoints) {
            if (column >= state.screen.columns) {
                fail("row expectation too wide, section '$section', line $lineNumber")
            }
            assertEquals(
                codePoint,
                state.screen.codePointAt(row, column),
                "row $row column $column, section '$section', line $lineNumber",
            )
            column += if (codePoint != 0 && state.screen.widthAt(row, column) == 2) 2 else 1
        }
        while (column < state.screen.columns) {
            assertEquals(
                0x20,
                state.screen.codePointAt(row, column),
                "trailing cell $row,$column, section '$section', line $lineNumber",
            )
            column++
        }
    }

    private fun parseBytesExpression(expression: String): ByteArray {
        val output = ByteArrayOutputStream()
        for (term in expression.split(" . ")) {
            parseBytesTerm(term.trim(), output)
        }
        return output.toByteArray()
    }

    private fun parseBytesTerm(
        term: String,
        output: ByteArrayOutputStream,
    ) {
        val repetition = Regex("^(.*)\\s*x\\s*(\\d+)$").matchEntire(term)
        if (repetition == null) {
            output.write(parseBytesLiteral(term))
            return
        }
        val bytes = parseBytesLiteral(repetition.groupValues[1].trim())
        repeat(repetition.groupValues[2].toInt()) {
            output.write(bytes)
        }
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

                'b' -> {
                    output.write(0x08)
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
}
