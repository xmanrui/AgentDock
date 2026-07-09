package com.agentdock.terminal

import com.agentdock.util.OperatingSystem

class ShellEscaper {
    fun escape(value: String, os: OperatingSystem): String {
        return when (os) {
            OperatingSystem.Windows -> escapeWindows(value)
            else -> escapeUnix(value)
        }
    }

    private fun escapeUnix(value: String): String {
        if (value.isEmpty()) return "''"
        if (value.matches(Regex("[A-Za-z0-9_./:=@%+-]+"))) return value
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun escapeWindows(value: String): String {
        if (value.isEmpty()) return "\"\""
        if (value.matches(Regex("[A-Za-z0-9_./:=@%+-]+"))) return value
        return "\"" + value.replace("\"", "\\\"") + "\""
    }
}
