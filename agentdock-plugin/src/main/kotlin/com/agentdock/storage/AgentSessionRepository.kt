package com.agentdock.storage

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus

class AgentSessionRepository(private val state: AgentDockProjectState) {
    fun all(includeArchived: Boolean = false): List<AgentSession> {
        return state.sessions
            .filter { includeArchived || !it.archived }
            .sortedWith(sessionComparator)
    }

    fun find(id: String): AgentSession? = state.sessions.firstOrNull { it.id == id }

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

    companion object {
        val sessionComparator: Comparator<AgentSession> = compareByDescending<AgentSession> { it.pinned }
            .thenBy { statusRank(it.status, it.archived) }
            .thenByDescending { it.updatedAt }
            .thenByDescending { it.createdAt }

        private fun statusRank(status: AgentSessionStatus, archived: Boolean): Int {
            if (archived) return 5
            return when (status) {
                AgentSessionStatus.Active -> 0
                AgentSessionStatus.Restorable -> 1
                AgentSessionStatus.MissingCli -> 2
                AgentSessionStatus.Error -> 3
                AgentSessionStatus.Archived -> 5
            }
        }
    }
}
