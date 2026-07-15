package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingSessionMatcherTest {
    @Test
    fun `matches the closest pending session for the same provider and directory`() {
        val discovered = discovered(createdAt = 12_000)
        val older = pending(id = "older", createdAt = 3_000)
        val closest = pending(id = "closest", createdAt = 10_000)

        assertEquals(
            "closest",
            PendingSessionMatcher.find(listOf(older, closest), discovered)?.id
        )
    }

    @Test
    fun `does not bind a different provider directory or established session`() {
        val discovered = discovered(createdAt = 12_000)
        val differentProvider = pending(id = "provider", createdAt = 10_000).copy(
            providerId = CLIProvider.CLAUDE_CODE_ID
        )
        val differentDirectory = pending(id = "directory", createdAt = 10_000).copy(cwd = "/tmp/other")
        val established = pending(id = "established", createdAt = 10_000).copy(
            providerSessionId = "existing",
            pendingProviderBinding = false
        )

        assertNull(
            PendingSessionMatcher.find(
                listOf(differentProvider, differentDirectory, established),
                discovered
            )
        )
    }

    @Test
    fun `does not bind stale provider history`() {
        val discovered = discovered(createdAt = 1_000)
        val pending = pending(id = "new", createdAt = 60_000)

        assertNull(PendingSessionMatcher.find(listOf(pending), discovered))
    }

    private fun pending(id: String, createdAt: Long): AgentSession {
        return AgentSession(
            id = id,
            providerId = CLIProvider.CODEX_ID,
            cwd = "/tmp/project/../project",
            createdAt = createdAt,
            updatedAt = createdAt,
            pendingProviderBinding = true
        )
    }

    private fun discovered(createdAt: Long): AgentSession {
        return AgentSession(
            id = "codex:provider-session",
            providerId = CLIProvider.CODEX_ID,
            providerSessionId = "provider-session",
            cwd = "/tmp/project",
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }
}
