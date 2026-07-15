package com.agentdock.storage

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSessionRepositoryTest {
    @Test
    fun `pinned sessions sort first then sessions sort by latest update`() {
        val state = AgentDockProjectState()
        val repository = AgentSessionRepository(state)

        repository.add(session("old-active", updatedAt = 1, pinned = false, status = AgentSessionStatus.Active))
        repository.add(session("pinned", updatedAt = 2, pinned = true, status = AgentSessionStatus.Restorable))
        repository.add(session("new-restorable", updatedAt = 3, pinned = false, status = AgentSessionStatus.Restorable))

        assertEquals(listOf("pinned", "new-restorable", "old-active"), repository.all().map { it.id })
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

    @Test
    fun `provider session ids find locally keyed sessions after discovery binding`() {
        val state = AgentDockProjectState()
        val repository = AgentSessionRepository(state)
        repository.add(
            session("local-session").copy(
                providerId = "codex",
                providerSessionId = "provider-session"
            )
        )

        assertEquals(
            "local-session",
            repository.findByProviderSession("codex", "provider-session")?.id
        )
        assertEquals(null, repository.findByProviderSession("claude-code", "provider-session"))
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
