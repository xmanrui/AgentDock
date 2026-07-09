package com.agentdock.terminal

import com.agentdock.util.OperatingSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalCommandExitMarkerTest {
    @Test
    fun `wraps unix command with exit marker write`() {
        val marker = File("/tmp/agentdock marker.exit")

        val command = TerminalCommandExitMarker.wrap(
            command = "codex resume abc",
            markerFile = marker,
            os = OperatingSystem.Mac
        )

        assertEquals(
            "(codex resume abc); __agentdock_exit=$?; printf '%s\\n' \"\$__agentdock_exit\" > '/tmp/agentdock marker.exit'; unset __agentdock_exit",
            command
        )
    }

    @Test
    fun `does not wrap windows command`() {
        val command = TerminalCommandExitMarker.wrap(
            command = "codex resume abc",
            markerFile = File("C:\\Temp\\agentdock.exit"),
            os = OperatingSystem.Windows
        )

        assertEquals("codex resume abc", command)
    }
}
