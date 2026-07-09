package com.agentdock.storage

import com.agentdock.model.AgentSession

data class AgentDockProjectState(
    var schemaVersion: Int = 1,
    var sessions: MutableList<AgentSession> = mutableListOf()
)
