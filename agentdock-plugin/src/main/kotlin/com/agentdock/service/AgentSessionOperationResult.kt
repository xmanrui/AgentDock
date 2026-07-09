package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.terminal.TerminalLaunchResult

sealed class AgentSessionOperationResult {
    data class Success(
        val session: AgentSession,
        val terminalResult: TerminalLaunchResult
    ) : AgentSessionOperationResult()

    data class Failure(
        val session: AgentSession?,
        val message: String
    ) : AgentSessionOperationResult()
}
