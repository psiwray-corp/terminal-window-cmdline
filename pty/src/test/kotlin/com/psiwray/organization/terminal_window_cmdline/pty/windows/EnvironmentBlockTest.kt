package com.psiwray.organization.terminal_window_cmdline.pty.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentBlockTest {
    @Test
    fun overridesAreCaseInsensitiveAndOutputIsSorted() {
        val block =
            EnvironmentBlock.build(
                overrides = mapOf("PATH" to "override", "TERM" to "xterm-256color"),
                parent = mapOf("Path" to "inherited", "Z" to "last", "A" to "first"),
            )
        assertEquals(
            listOf("A=first", "PATH=override", "TERM=xterm-256color", "Z=last"),
            decode(block),
        )
    }

    @Test
    fun blockIsDoubleNullTerminated() {
        val block = EnvironmentBlock.build(overrides = emptyMap(), parent = mapOf("A" to "1"))
        assertEquals("A=1", decode(block).single())
        assertEquals(0, block[block.size - 1].toInt())
        assertEquals(0, block[block.size - 2].toInt())
    }

    @Test
    fun unicodeValuesSurvive() {
        val block = EnvironmentBlock.build(overrides = emptyMap(), parent = mapOf("GREETING" to "héllo"))
        assertEquals("GREETING=héllo", decode(block).single())
    }

    @Test
    fun rejectsInvalidKeysAndValues() {
        assertFailsWith<IllegalArgumentException> {
            EnvironmentBlock.build(overrides = mapOf("" to "value"), parent = emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            EnvironmentBlock.build(overrides = mapOf("A=B" to "value"), parent = emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            EnvironmentBlock.build(overrides = mapOf("A" to "bad\u0000value"), parent = emptyMap())
        }
    }

    private fun decode(block: ShortArray): List<String> {
        val result = ArrayList<String>()
        val builder = StringBuilder()
        for (unit in block) {
            if (unit.toInt() == 0) {
                if (builder.isNotEmpty()) {
                    result.add(builder.toString())
                    builder.setLength(0)
                }
            } else {
                builder.append(unit.toInt().toChar())
            }
        }
        return result
    }
}
