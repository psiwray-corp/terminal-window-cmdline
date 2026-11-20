package com.psiwray.organization.terminal_window_cmdline.vt.state

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ResponderTest {
    @Test
    fun writesWholeResponsesAndDropsOverflow() {
        val responder = Responder(4)

        responder.write(byteArrayOf(1, 2, 3))
        assertEquals(3, responder.available)

        responder.write(byteArrayOf(4, 5))
        assertEquals(3, responder.available)
        assertEquals(1, responder.droppedResponses)

        val first = ByteArray(2)
        assertEquals(2, responder.read(first))
        assertContentEquals(byteArrayOf(1, 2), first)

        val second = ByteArray(4)
        assertEquals(1, responder.read(second))
        assertContentEquals(byteArrayOf(3), second.copyOf(1))
        assertEquals(0, responder.available)
    }

    @Test
    fun clearRemovesQueuedResponses() {
        val responder = Responder(8)
        responder.write(byteArrayOf(1, 2, 3))

        responder.clear()

        assertEquals(0, responder.available)
        assertEquals(0, responder.read(ByteArray(1)))
    }
}
