package com.agentdock.terminal

import com.agentdock.util.OperatingSystem
import java.io.File

object TerminalCommandExitMarker {
    fun supports(os: OperatingSystem): Boolean {
        return os != OperatingSystem.Windows
    }

    fun markerFile(token: String): File {
        return File(File(System.getProperty("java.io.tmpdir"), "agentdock"), "$token.exit")
    }

    fun wrap(command: String, markerFile: File, os: OperatingSystem, shellEscaper: ShellEscaper = ShellEscaper()): String {
        if (!supports(os)) return command
        val markerPath = shellEscaper.escape(markerFile.absolutePath, os)
        return "($command); __agentdock_exit=$?; printf '%s\\n' \"\$__agentdock_exit\" > $markerPath; unset __agentdock_exit"
    }
}
