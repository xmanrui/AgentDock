package com.agentdock.storage

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSessionRepositoryTest {
    @Test
    fun `pinned active sessions sort first`() {
        val state = AgentDockProjectState()
        val repository = AgentSessionRepository(state)

        repository.add(session("old", updatedAt = 1, pinned = false, status = AgentSessionStatus.Active))
        repository.add(session("pinned", updatedAt = 2, pinned = true, status = AgentSessionStatus.Restorable))
        repository.add(session("new", updatedAt = 3, pinned = false, status = AgentSessionStatus.Active))

        assertEquals(listOf("pinned", "new", "old"), repository.all().map { it.id })
    }

    @Test
    fun `archived sessions are hidden by default`() {
        val state = AgentDockProjectState()
        val repository = AgentSessionRepository(state)

        repository.add(session("visible", archived = false))
        repository.add(session("archived", archived = true, status = AgentSessionStatus.Archived))

        assertEquals(listOf("visible"), repository.all().map { it.id })
        assertTrue(repository.all(includeArchived = true).any { it.id == "archived" })
        assertFalse(repository.all().any { it.id == "archived" })
    }

    private fun session(
        id: String,
        updatedAt: Long = 1,
        pinned: Boolean = false,
        archived: Boolean = false,
        status: AgentSessionStatus = AgentSessionStatus.Restorable
    ): AgentSession {
        return AgentSession(
            id = id,
            name = id,
            providerId = "codex",
            cwd = "/tmp",
            status = status,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            pinned = pinned,
            archived = archived
        )
    }
}
