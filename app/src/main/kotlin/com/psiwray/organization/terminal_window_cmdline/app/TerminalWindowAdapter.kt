package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.input.CursorEnterEvent
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.MouseButtonEvent
import com.psiwray.organization.terminal_window.input.MouseMoveEvent
import com.psiwray.organization.terminal_window_cmdline.pty.ByteRing
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import com.psiwray.organization.terminal_window_cmdline.vt.input.Action
import com.psiwray.organization.terminal_window_cmdline.vt.input.InputEncoder
import com.psiwray.organization.terminal_window_cmdline.vt.input.InputSink
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import com.psiwray.organization.terminal_window.input.CharEvent as WindowCharEvent
import com.psiwray.organization.terminal_window.input.FocusEvent as WindowFocusEvent
import com.psiwray.organization.terminal_window.input.Key as WindowKey
import com.psiwray.organization.terminal_window.input.KeyAction as WindowKeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent as WindowKeyEvent
import com.psiwray.organization.terminal_window.input.Modifiers as WindowModifiers
import com.psiwray.organization.terminal_window.input.MouseButton as WindowMouseButton
import com.psiwray.organization.terminal_window.input.ResizeEvent as WindowResizeEvent
import com.psiwray.organization.terminal_window.input.ScrollEvent as WindowScrollEvent
import com.psiwray.organization.terminal_window_cmdline.vt.input.CharEvent as VtCharEvent
import com.psiwray.organization.terminal_window_cmdline.vt.input.FocusEvent as VtFocusEvent
import com.psiwray.organization.terminal_window_cmdline.vt.input.Key as VtKey
import com.psiwray.organization.terminal_window_cmdline.vt.input.KeyEvent as VtKeyEvent
import com.psiwray.organization.terminal_window_cmdline.vt.input.Modifiers as VtModifiers
import com.psiwray.organization.terminal_window_cmdline.vt.input.MouseButton as VtMouseButton
import com.psiwray.organization.terminal_window_cmdline.vt.input.MouseEvent as VtMouseEvent
import com.psiwray.organization.terminal_window_cmdline.vt.input.ScrollEvent as VtScrollEvent

/**
 * Connects one [ProcessSession] to one [TerminalWindow].
 *
 * The owner thread drives [drainAndParse] and [project]. The reader thread
 * publishes bytes into a bounded ring, the writer thread serializes encoded
 * input and query responses, and the watcher publishes the exit snapshot.
 */
internal class TerminalWindowAdapter(
    private val window: TerminalWindow,
    private val session: ProcessSession,
    private val theme: Theme,
) : InputListener {
    private val state: TerminalState = TerminalState(window.matrix.rows, window.matrix.columns)
    private val parser: Parser = Parser(state)
    private val ring: ByteRing = ByteRing(PipelineLimits.BYTE_RING_CAPACITY)
    private val writeQueue: ArrayBlockingQueue<ByteArray> =
        ArrayBlockingQueue(PipelineLimits.WRITE_QUEUE_CAPACITY)
    private val readBuffer: ByteArray = ByteArray(PipelineLimits.READ_CHUNK_BYTES)
    private val responseBuffer: ByteArray = ByteArray(PipelineLimits.READ_CHUNK_BYTES)
    private val encoder: InputEncoder =
        InputEncoder(
            state,
            InputSink { bytes -> enqueue(bytes) },
        )
    private val pressed: BooleanArray = BooleanArray(VtMouseButton.entries.size)
    private val stopped: AtomicBoolean = AtomicBoolean(false)
    private val failure: AtomicReference<Throwable?> = AtomicReference()
    private val droppedWrites: AtomicLong = AtomicLong()

    private var readerThread: Thread? = null
    private var writerThread: Thread? = null
    private var watcherThread: Thread? = null
    private var started: Boolean = false

    /** Latest child exit status, set by the watcher thread. */
    @Volatile
    var exit: ProcessExit? = null
        private set

    /** First reader/writer failure, if any. */
    val firstFailure: Throwable?
        get() = failure.get()

    /** Number of encoded writes dropped because the writer queue was full. */
    val droppedWriteCount: Long
        get() = droppedWrites.get()

    /** Starts the reader, writer, and watcher threads and installs input handling. */
    fun start() {
        if (started) {
            return
        }
        started = true
        window.addInputListener(this)
        readerThread =
            thread(name = "terminal-reader", isDaemon = true) {
                readLoop()
            }
        writerThread =
            thread(name = "terminal-writer", isDaemon = true) {
                writeLoop()
            }
        watcherThread =
            thread(name = "terminal-watcher", isDaemon = true) {
                watchLoop()
            }
    }

    /** Resizes VT state and the session when the matrix dimensions changed. */
    fun syncSize(): Boolean {
        val matrix = window.matrix
        if (matrix.columns == state.screen.columns && matrix.rows == state.screen.rows) {
            return false
        }
        state.resize(matrix.rows, matrix.columns)
        session.resize(TerminalSize(matrix.columns, matrix.rows))
        return true
    }

    /**
     * Drains and parses up to the per-frame byte/time budget.
     *
     * @return bytes parsed in this call.
     */
    fun drainAndParse(): Int {
        val startedAt = System.nanoTime()
        var parsed = 0
        while (parsed < PipelineLimits.PARSE_BYTE_BUDGET) {
            val available = ring.available()
            if (available == 0) {
                break
            }
            val take = minOf(available, readBuffer.size, PipelineLimits.PARSE_BYTE_BUDGET - parsed)
            val count = ring.read(readBuffer, 0, take)
            if (count <= 0) {
                break
            }
            parser.write(readBuffer, 0, count)
            parsed += count
            if (System.nanoTime() - startedAt >= PipelineLimits.PARSE_TIME_BUDGET_NANOS) {
                break
            }
        }
        drainResponses()
        return parsed
    }

    /**
     * Drains the ring until the reader observes EOF, then parses the rest.
     *
     * The reader can be blocked in [ByteRing.write] while the ring is full.
     * Joining before consuming the ring would deadlock the shutdown path.
     */
    fun drainRemaining() {
        val reader = readerThread
        while (reader != null && reader.isAlive) {
            val available = ring.available()
            if (available == 0) {
                try {
                    Thread.sleep(1L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
                continue
            }
            val take = minOf(available, readBuffer.size)
            val count = ring.read(readBuffer, 0, take)
            if (count <= 0) {
                break
            }
            parser.write(readBuffer, 0, count)
        }
        while (true) {
            val available = ring.available()
            if (available == 0) {
                val eof = ring.read(readBuffer, 0, 1)
                if (eof < 0) {
                    break
                }
                parser.write(readBuffer, 0, eof)
                continue
            }
            val take = minOf(available, readBuffer.size)
            val count = ring.read(readBuffer, 0, take)
            if (count <= 0) {
                break
            }
            parser.write(readBuffer, 0, count)
        }
        drainResponses()
    }

    /** Projects the active visible screen and block cursor into the matrix. */
    fun project() {
        ScreenProjection.project(
            view = state.screen,
            cursorRow = state.cursorRow,
            cursorColumn = state.cursorColumn,
            cursorVisible = state.cursorVisible,
            screenReverse = state.screenReverse,
            theme = theme,
            target = window.matrix,
        )
    }

    /** Stops the pipeline and ensures the child is terminated. */
    fun close() {
        if (!stopped.compareAndSet(false, true)) {
            return
        }
        window.removeInputListener(this)
        session.close()
        ring.close()
        writeQueue.offer(POISON)
        readerThread?.interrupt()
        writerThread?.interrupt()
        watcherThread?.interrupt()
        join(readerThread)
        join(writerThread)
        join(watcherThread)
    }

    override fun onKey(event: WindowKeyEvent) {
        val key = WINDOW_TO_VT_KEY[event.key] ?: VtKey.UNKNOWN
        encoder.encode(
            VtKeyEvent(
                key = key,
                modifiers = event.modifiers.toVt(),
                action = event.action.toVt(),
            ),
        )
    }

    override fun onChar(event: WindowCharEvent) {
        encoder.encode(VtCharEvent(event.codePoint))
    }

    override fun onFocus(event: WindowFocusEvent) {
        encoder.encode(VtFocusEvent(event.focused))
    }

    override fun onMouseButton(event: MouseButtonEvent) {
        val button = event.button.toVt() ?: return
        val action = event.action.toVt()
        if (action == Action.PRESS) {
            pressed[button.ordinal] = true
        } else if (action == Action.RELEASE) {
            pressed[button.ordinal] = false
        }
        encoder.encode(
            VtMouseEvent(
                column = event.column,
                row = event.row,
                button = button,
                action = action,
                modifiers = event.modifiers.toVt(),
            ),
        )
    }

    override fun onMouseMove(event: MouseMoveEvent) {
        encoder.encode(
            VtMouseEvent(
                column = event.column,
                row = event.row,
                button = heldButton(),
                action = Action.MOVE,
                modifiers = currentModifiers(),
            ),
        )
    }

    override fun onScroll(event: WindowScrollEvent) {
        val delta = scrollDelta(event)
        if (delta == 0) {
            return
        }
        val cursor = window.cursorPosition()
        encoder.encode(
            VtScrollEvent(
                column = cursor.column,
                row = cursor.row,
                delta = delta,
                modifiers = currentModifiers(),
            ),
        )
    }

    override fun onResize(event: WindowResizeEvent) {
        syncSize()
        project()
    }

    override fun onCursorEnter(event: CursorEnterEvent) = Unit

    private fun readLoop() {
        try {
            while (!stopped.get()) {
                val count = session.read(readBuffer, 0, readBuffer.size)
                if (count < 0) {
                    break
                }
                if (count > 0) {
                    ring.write(readBuffer, 0, count)
                }
            }
        } catch (failure: IOException) {
            if (!stopped.get()) {
                this.failure.compareAndSet(null, failure)
            }
        } catch (failure: RuntimeException) {
            if (!stopped.get()) {
                this.failure.compareAndSet(null, failure)
            }
        } finally {
            ring.closeInput()
        }
    }

    private fun writeLoop() {
        try {
            while (!stopped.get()) {
                val bytes = writeQueue.take()
                if (bytes === POISON) {
                    break
                }
                session.write(bytes, 0, bytes.size)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: IOException) {
            if (!stopped.get()) {
                this.failure.compareAndSet(null, failure)
            }
        }
    }

    private fun watchLoop() {
        while (!stopped.get()) {
            val snapshot = session.exit
            if (snapshot != null) {
                exit = snapshot
                return
            }
            try {
                Thread.sleep(PipelineLimits.WATCH_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun drainResponses() {
        while (state.responses.available > 0) {
            val count = state.responses.read(responseBuffer)
            if (count <= 0) {
                return
            }
            enqueue(responseBuffer.copyOf(count))
        }
    }

    private fun enqueue(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        if (!writeQueue.offer(bytes)) {
            droppedWrites.incrementAndGet()
        }
    }

    private fun heldButton(): VtMouseButton? {
        for (button in PRESSABLE_BUTTONS) {
            if (pressed[button.ordinal]) {
                return button
            }
        }
        return null
    }

    private fun currentModifiers(): VtModifiers {
        var result = VtModifiers.NONE
        if (window.isKeyPressed(WindowKey.LEFT_SHIFT) || window.isKeyPressed(WindowKey.RIGHT_SHIFT)) {
            result = result or VtModifiers.SHIFT
        }
        if (window.isKeyPressed(WindowKey.LEFT_CONTROL) || window.isKeyPressed(WindowKey.RIGHT_CONTROL)) {
            result = result or VtModifiers.CONTROL
        }
        if (window.isKeyPressed(WindowKey.LEFT_ALT) || window.isKeyPressed(WindowKey.RIGHT_ALT)) {
            result = result or VtModifiers.ALT
        }
        if (window.isKeyPressed(WindowKey.LEFT_SUPER) || window.isKeyPressed(WindowKey.RIGHT_SUPER)) {
            result = result or VtModifiers.SUPER
        }
        return result
    }

    private fun scrollDelta(event: WindowScrollEvent): Int {
        if (event.yOffset == 0.0) {
            return 0
        }
        val rounded = event.yOffset.roundToInt()
        return if (rounded == 0) {
            if (event.yOffset > 0) 1 else -1
        } else {
            rounded
        }
    }

    private fun join(thread: Thread?) {
        if (thread == null) {
            return
        }
        try {
            thread.join(THREAD_JOIN_MILLIS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun WindowModifiers.toVt(): VtModifiers {
        var result = VtModifiers.NONE
        if (has(WindowModifiers.SHIFT)) {
            result = result or VtModifiers.SHIFT
        }
        if (has(WindowModifiers.CONTROL)) {
            result = result or VtModifiers.CONTROL
        }
        if (has(WindowModifiers.ALT)) {
            result = result or VtModifiers.ALT
        }
        if (has(WindowModifiers.SUPER)) {
            result = result or VtModifiers.SUPER
        }
        if (has(WindowModifiers.CAPS_LOCK)) {
            result = result or VtModifiers.CAPS_LOCK
        }
        if (has(WindowModifiers.NUM_LOCK)) {
            result = result or VtModifiers.NUM_LOCK
        }
        return result
    }

    private fun WindowKeyAction.toVt(): Action =
        when (this) {
            WindowKeyAction.PRESS -> Action.PRESS
            WindowKeyAction.REPEAT -> Action.REPEAT
            WindowKeyAction.RELEASE -> Action.RELEASE
        }

    private fun WindowMouseButton.toVt(): VtMouseButton? =
        when (this) {
            WindowMouseButton.LEFT -> VtMouseButton.LEFT
            WindowMouseButton.MIDDLE -> VtMouseButton.MIDDLE
            WindowMouseButton.RIGHT -> VtMouseButton.RIGHT
            else -> null
        }

    private companion object {
        private const val THREAD_JOIN_MILLIS: Long = 2_000L
        private val POISON: ByteArray = ByteArray(0)
        private val PRESSABLE_BUTTONS: List<VtMouseButton> =
            listOf(VtMouseButton.LEFT, VtMouseButton.MIDDLE, VtMouseButton.RIGHT)
        private val WINDOW_TO_VT_KEY: Map<WindowKey, VtKey> =
            WindowKey.entries.associateWith { windowKey ->
                VtKey.entries.firstOrNull { it.name == windowKey.name } ?: VtKey.UNKNOWN
            }
    }
}
