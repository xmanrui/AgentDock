package com.agentdock.model

import com.agentdock.util.OperatingSystem

data class ProviderCommandContext(
    val provider: CLIProvider,
    val session: AgentSession,
    val projectPath: String,
    val shell: String,
    val os: OperatingSystem
)
