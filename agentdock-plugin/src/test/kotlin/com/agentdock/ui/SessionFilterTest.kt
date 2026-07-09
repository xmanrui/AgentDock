package com.agentdock.ui

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider
import com.agentdock.storage.SessionFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFilterTest {
    private val providers = CLIProvider.defaultProviders()

    @Test
    fun `filters by provider`() {
        val sessions = listOf(
            session("a", CLIProvider.CODEX_ID),
            session("b", CLIProvider.CLAUDE_CODE_ID)
        )

        val result = SessionFilter.filter(sessions, providers, providerId = CLIProvider.CLAUDE_CODE_ID)

        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `filters by chinese summary`() {
        val sessions = listOf(
            session("a", CLIProvider.CODEX_ID, summary = "修复导出报告空数据"),
            session("b", CLIProvider.CLAUDE_CODE_ID, summary = "重构 dashboard")
        )

        val result = SessionFilter.filter(sessions, providers, query = "空数据")

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `filters archived status`() {
        val sessions = listOf(
            session("a", CLIProvider.CODEX_ID),
            session("b", CLIProvider.CLAUDE_CODE_ID, archived = true, status = AgentSessionStatus.Archived)
        )

        val result = SessionFilter.filter(
            sessions,
            providers,
            status = AgentSessionStatus.Archived,
            includeArchived = true
        )

        assertEquals(listOf("b"), result.map { it.id })
    }

    private fun session(
        id: String,
        providerId: String,
        summary: String = "",
        archived: Boolean = false,
        status: AgentSessionStatus = AgentSessionStatus.Restorable
    ): AgentSession {
        return AgentSession(
            id = id,
            name = id,
            providerId = providerId,
            cwd = "/tmp/project",
            summary = summary,
            archived = archived,
            status = status,
            updatedAt = 1,
            createdAt = 1
        )
    }
}
