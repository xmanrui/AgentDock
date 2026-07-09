package com.agentdock.terminal

interface TerminalLauncher {
    fun launch(command: String, cwd: String, presentation: TerminalTabPresentation): TerminalLaunchResult
}
