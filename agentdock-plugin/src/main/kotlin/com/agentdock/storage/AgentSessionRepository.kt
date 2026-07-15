package com.agentdock.storage

import com.agentdock.model.AgentSession

class AgentSessionRepository(private val state: AgentDockProjectState) {
    fun all(includeArchived: Boolean = false): List<AgentSession> {
        return state.sessions
            .filter { includeArchived || !it.archived }
            .sortedWith(sessionComparator)
    }

    fun find(id: String): AgentSession? = state.sessions.firstOrNull { it.id == id }

    fun findByProviderSession(providerId: String, providerSessionId: String): AgentSession? {
        return state.sessions.firstOrNull {
            it.providerId == providerId && it.providerSessionId == providerSessionId
        }
    }

    fun add(session: AgentSession): AgentSession {
        state.sessions.removeAll { it.id == session.id }
        state.sessions.add(session)
        return session
    }

    fun update(session: AgentSession): AgentSession {
        val index = state.sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            state.sessions[index] = session
        } else {
            state.sessions.add(session)
        }
        return session
    }

    fun remove(sessionId: String): Boolean = state.sessions.removeAll { it.id == sessionId }

    companion object {
        val sessionComparator: Comparator<AgentSession> = compareByDescending<AgentSession> { it.pinned }
            .thenByDescending { it.updatedAt }
            .thenByDescending { it.createdAt }
    }
}
