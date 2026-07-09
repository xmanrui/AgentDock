package com.agentdock.terminal

import com.agentdock.util.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class ShellEscaperTest {
    private val escaper = ShellEscaper()

    @Test
    fun `does not quote simple unix values`() {
        assertEquals("codex_123", escaper.escape("codex_123", OperatingSystem.Mac))
    }

    @Test
    fun `quotes unix values with spaces`() {
        assertEquals("'/tmp/project with spaces'", escaper.escape("/tmp/project with spaces", OperatingSystem.Mac))
    }

    @Test
    fun `escapes single quote for unix shells`() {
        assertEquals("'task '\"'\"'quoted'\"'\"''", escaper.escape("task 'quoted'", OperatingSystem.Linux))
    }

    @Test
    fun `quotes windows values with spaces`() {
        assertEquals("\"C:\\Project Files\"", escaper.escape("C:\\Project Files", OperatingSystem.Windows))
    }
}
