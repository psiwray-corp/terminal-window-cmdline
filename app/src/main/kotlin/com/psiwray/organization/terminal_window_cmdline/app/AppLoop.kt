package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.TerminalWindowService
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window_cmdline.pty.PipeSession
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.posix.PosixPtySession
import com.psiwray.organization.terminal_window_cmdline.pty.windows.ConPtySession
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Owns the main-thread service and the window owner thread. */
internal object AppLoop {
    private const val DEFAULT_FONT_RESOURCE: String = "fonts/ascii-8x16.twf"

    fun run(config: LaunchConfig) {
        val font = loadFont(config.font)
        val session = createSession(config)
        val failure = AtomicReference<Throwable?>()
        try {
            val service = initializeService()
            service.use { svc ->
                val window =
                    svc.openWindow(
                        TerminalWindowConfig(
                            font = font,
                            title = "terminal-window-cmdline",
                            columns = config.columns,
                            rows = config.rows,
                            defaultForeground = config.theme.foreground,
                            defaultBackground = config.theme.background,
                        ),
                    )
                window.releaseContext()
                val owner =
                    thread(name = "terminal-owner", isDaemon = false, start = false) {
                        runOwner(window, session, config, failure)
                    }
                owner.start()
                try {
                    svc.runEventLoop()
                } catch (cause: Throwable) {
                    window.requestClose()
                    throw cause
                } finally {
                    owner.join()
                }
            }
        } finally {
            session.close()
        }
        failure.get()?.let { throw it }
    }

    private fun runOwner(
        window: TerminalWindow,
        session: ProcessSession,
        config: LaunchConfig,
        failure: AtomicReference<Throwable?>,
    ) {
        try {
            window.use { owned ->
                owned.acquireContext()
                val adapter = TerminalWindowAdapter(owned, session, config.theme)
                try {
                    adapter.start()
                    ownerLoop(owned, adapter)
                } finally {
                    adapter.close()
                }
            }
        } catch (cause: Throwable) {
            failure.compareAndSet(null, cause)
            if (!window.isClosed) {
                window.requestClose()
            }
        }
    }

    private fun ownerLoop(
        window: TerminalWindow,
        adapter: TerminalWindowAdapter,
    ) {
        while (!window.shouldClose) {
            // The frame drain happens before parsing, so input cannot be
            // starved by a flood of child output.
            window.frame()
            if (window.shouldClose) {
                return
            }
            adapter.syncSize()
            adapter.drainAndParse()
            adapter.project()

            val exit = adapter.exit
            if (exit != null) {
                adapter.drainRemaining()
                adapter.project()
                window.requestRepaint()
                window.frame()
                return
            }
            adapter.firstFailure?.let { throw it }

            try {
                Thread.sleep(PipelineLimits.FRAME_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun createSession(config: LaunchConfig): ProcessSession {
        if (config.pipe) {
            return PipeSession(
                command = config.command,
                workingDirectory = config.workingDirectory,
                environment = config.childEnvironment(),
            )
        }
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("windows")) {
            return ConPtySession(
                command = config.command,
                workingDirectory = config.workingDirectory,
                environment = config.childEnvironment(),
                size = config.size,
            )
        }
        return PosixPtySession(
            command = config.command,
            workingDirectory = config.workingDirectory,
            environment = config.childEnvironment(),
            size = config.size,
        )
    }

    private fun loadFont(path: Path?): BitmapFont {
        if (path != null) {
            return BitmapFont.parse(path)
        }
        val stream =
            AppLoop::class.java.classLoader.getResourceAsStream(DEFAULT_FONT_RESOURCE)
                ?: throw IllegalStateException("bundled font not found: $DEFAULT_FONT_RESOURCE")
        return stream.use { BitmapFont.parse(it) }
    }

    private fun initializeService(): TerminalWindowService {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("linux") && !System.getenv("DISPLAY").isNullOrBlank()) {
            GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11)
        }
        return TerminalWindowService.initialize()
    }
}
