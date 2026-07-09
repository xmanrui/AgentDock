package com.agentdock.storage

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider

object SessionFilter {
    fun filter(
        sessions: List<AgentSession>,
        providers: List<CLIProvider>,
        query: String = "",
        providerId: String? = null,
        status: AgentSessionStatus? = null,
        includeArchived: Boolean = false
    ): List<AgentSession> {
        val providerNames = providers.associate { it.id to it.displayName }
        val normalizedQuery = query.trim().lowercase()

        return sessions
            .asSequence()
            .filter { includeArchived || !it.archived }
            .filter { providerId.isNullOrBlank() || it.providerId == providerId }
            .filter { status == null || it.status == status || (status == AgentSessionStatus.Archived && it.archived) }
            .filter { session ->
                if (normalizedQuery.isBlank()) {
                    true
                } else {
                    val bag = buildList {
                        add(session.name)
                        add(session.summary)
                        add(session.cwd)
                        add(providerNames[session.providerId].orEmpty())
                        addAll(session.linkedFiles)
                    }.joinToString(" ").lowercase()
                    normalizedQuery in bag
                }
            }
            .sortedWith(AgentSessionRepository.sessionComparator)
            .toList()
    }
}
