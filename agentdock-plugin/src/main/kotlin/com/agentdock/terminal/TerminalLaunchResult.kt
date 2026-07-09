package com.agentdock.terminal

sealed class TerminalLaunchResult {
    data class Sent(val message: String = "Command sent to terminal") : TerminalLaunchResult()
    data class ClipboardFallback(val message: String) : TerminalLaunchResult()
    data class Failed(val message: String) : TerminalLaunchResult()
}
