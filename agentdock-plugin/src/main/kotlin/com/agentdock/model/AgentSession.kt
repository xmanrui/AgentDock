package com.agentdock.model

data class AgentSession(
    var id: String = "",
    var projectId: String = "",
    var projectPath: String = "",
    var name: String = "",
    var providerId: String = "",
    var status: AgentSessionStatus = AgentSessionStatus.Restorable,
    var cwd: String = "",
    var providerSessionId: String? = null,
    var terminalTabId: String? = null,
    var summary: String = "",
    var linkedFiles: MutableList<String> = mutableListOf(),
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var lastError: String? = null
)
