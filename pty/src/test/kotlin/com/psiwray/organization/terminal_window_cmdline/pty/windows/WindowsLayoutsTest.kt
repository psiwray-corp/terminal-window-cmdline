package com.psiwray.organization.terminal_window_cmdline.pty.windows

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsLayoutsTest {
    @Test
    fun coordMatchesWindowsAbi() {
        assertLayout(WindowsLayouts.COORD, size = 4, alignment = 2)
        assertEquals(0, offset(WindowsLayouts.COORD, "X"))
        assertEquals(2, offset(WindowsLayouts.COORD, "Y"))
    }

    @Test
    fun securityAttributesMatchWindowsAbi() {
        assertLayout(WindowsLayouts.SECURITY_ATTRIBUTES, size = 24, alignment = 8)
        assertEquals(0, offset(WindowsLayouts.SECURITY_ATTRIBUTES, "nLength"))
        assertEquals(8, offset(WindowsLayouts.SECURITY_ATTRIBUTES, "lpSecurityDescriptor"))
        assertEquals(16, offset(WindowsLayouts.SECURITY_ATTRIBUTES, "bInheritHandle"))
    }

    @Test
    fun startupInfoMatchesWindowsAbi() {
        assertLayout(WindowsLayouts.STARTUPINFOW, size = 104, alignment = 8)
        assertEquals(0, offset(WindowsLayouts.STARTUPINFOW, "cb"))
        assertEquals(8, offset(WindowsLayouts.STARTUPINFOW, "lpReserved"))
        assertEquals(16, offset(WindowsLayouts.STARTUPINFOW, "lpDesktop"))
        assertEquals(24, offset(WindowsLayouts.STARTUPINFOW, "lpTitle"))
        assertEquals(60, offset(WindowsLayouts.STARTUPINFOW, "dwFlags"))
        assertEquals(64, offset(WindowsLayouts.STARTUPINFOW, "wShowWindow"))
        assertEquals(72, offset(WindowsLayouts.STARTUPINFOW, "lpReserved2"))
        assertEquals(80, offset(WindowsLayouts.STARTUPINFOW, "hStdInput"))
        assertEquals(88, offset(WindowsLayouts.STARTUPINFOW, "hStdOutput"))
        assertEquals(96, offset(WindowsLayouts.STARTUPINFOW, "hStdError"))
    }

    @Test
    fun startupInfoExMatchesWindowsAbi() {
        assertLayout(WindowsLayouts.STARTUPINFOEXW, size = 112, alignment = 8)
        assertEquals(0, offset(WindowsLayouts.STARTUPINFOEXW, "StartupInfo"))
        assertEquals(104, offset(WindowsLayouts.STARTUPINFOEXW, "lpAttributeList"))
    }

    @Test
    fun processInformationMatchesWindowsAbi() {
        assertLayout(WindowsLayouts.PROCESS_INFORMATION, size = 24, alignment = 8)
        assertEquals(0, offset(WindowsLayouts.PROCESS_INFORMATION, "hProcess"))
        assertEquals(8, offset(WindowsLayouts.PROCESS_INFORMATION, "hThread"))
        assertEquals(16, offset(WindowsLayouts.PROCESS_INFORMATION, "dwProcessId"))
        assertEquals(20, offset(WindowsLayouts.PROCESS_INFORMATION, "dwThreadId"))
    }

    private fun offset(
        layout: MemoryLayout,
        name: String,
    ): Long = layout.byteOffset(PathElement.groupElement(name))

    private fun assertLayout(
        layout: MemoryLayout,
        size: Long,
        alignment: Long,
    ) {
        assertEquals(size, layout.byteSize())
        assertEquals(alignment, layout.byteAlignment())
    }
}
