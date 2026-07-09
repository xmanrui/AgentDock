package com.agentdock.util

enum class OperatingSystem {
    Mac,
    Linux,
    Windows,
    Other;

    companion object {
        fun current(): OperatingSystem {
            val name = System.getProperty("os.name").lowercase()
            return when {
                "mac" in name || "darwin" in name -> Mac
                "win" in name -> Windows
                "linux" in name -> Linux
                else -> Other
            }
        }
    }
}
