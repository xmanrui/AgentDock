package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import com.agentdock.storage.AgentDockProjectState
import com.intellij.util.xmlb.XmlSerializer
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSessionTokenUsageServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `computes Codex history from cumulative deltas and keeps a 14 day series`() = withHome { home ->
        val source = File(home, "codex.jsonl")
        source.writeText(
            listOf(
                "not-json",
                codexUsage("2026-07-01T10:00:00Z", cumulative = 100, last = 100),
                codexUsage("2026-07-02T10:00:00Z", cumulative = 150, last = 50),
                codexUsage("2026-07-02T10:00:01Z", cumulative = 150, last = 50),
                codexUsage("2026-07-10T10:00:00Z", cumulative = 30, last = 30)
            ).joinToString("\n")
        )
        val session = session(CLIProvider.CODEX_ID, source)
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)

        val usage = service.load(session)

        assertEquals(180L, usage.totalTokens)
        assertEquals(14, usage.dailyTokens.size)
        assertEquals(100L, usage.dailyTokens[3])
        assertEquals(50L, usage.dailyTokens[4])
        assertEquals(30L, usage.dailyTokens[12])
        assertEquals(usage, service.cached(session))
        assertTrue(session.tokenUsageCached)
        assertEquals(180L, session.tokenUsageTotal)
        assertEquals(usage.dailyTokens, session.tokenUsageDaily)
    }

    @Test
    fun `reuses a persisted cache when the history file has not changed`() = withHome { home ->
        val source = File(home, "codex.jsonl")
        source.writeText(codexUsage("2026-07-10T10:00:00Z", cumulative = 120, last = 120))
        val originalModifiedAt = source.lastModified()
        val originalLength = source.length()
        val session = session(CLIProvider.CODEX_ID, source)

        val usage = LocalSessionTokenUsageService(LocalSessionContentService(home), clock).load(session)
        source.writeText("x".repeat(originalLength.toInt()))
        source.setLastModified(originalModifiedAt)

        val restoredService = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)

        assertEquals(usage, restoredService.cached(session))
        assertEquals(usage, restoredService.load(session))
    }

    @Test
    fun `token usage cache survives project state serialization`() = withHome { home ->
        val source = File(home, "codex.jsonl")
        source.writeText(codexUsage("2026-07-10T10:00:00Z", cumulative = 90, last = 90))
        val session = session(CLIProvider.CODEX_ID, source)
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)
        val usage = service.load(session)

        val state = AgentDockProjectState(sessions = mutableListOf(session))
        val restoredState = XmlSerializer.deserialize(
            XmlSerializer.serialize(state),
            AgentDockProjectState::class.java
        )
        val restoredSession = restoredState.sessions.single()

        assertEquals(usage, LocalSessionTokenUsageService(LocalSessionContentService(home), clock).cached(restoredSession))
    }

    @Test
    fun `deduplicates Claude messages and includes cached input tokens`() = withHome { home ->
        val source = File(home, "claude.jsonl")
        source.writeText(
            listOf(
                claudeUsage("2026-06-20T10:00:00Z", "old", 100, 20, 0, 0),
                claudeUsage("2026-07-05T10:00:00Z", "message-a", 2, 10, 20, 30),
                claudeUsage("2026-07-05T10:00:01Z", "message-a", 2, 10, 20, 30),
                claudeUsage("2026-07-06T10:00:00Z", "message-a", 2, 15, 20, 30),
                claudeUsage("2026-07-10T10:00:00Z", "message-b", 3, 4, 5, 6)
            ).joinToString("\n")
        )
        val session = session(CLIProvider.CLAUDE_CODE_ID, source)
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)

        val usage = service.load(session)

        assertEquals(205L, usage.totalTokens)
        assertEquals(67L, usage.dailyTokens[8])
        assertEquals(18L, usage.dailyTokens[12])
        assertEquals(85L, usage.dailyTokens.sum())
    }

    @Test
    fun `returns unavailable usage when no local history exists`() = withHome { home ->
        val session = AgentSession(
            id = "codex:missing",
            providerId = CLIProvider.CODEX_ID,
            providerSessionId = "missing"
        )

        val usage = LocalSessionTokenUsageService(LocalSessionContentService(home), clock).load(session)

        assertNull(usage.totalTokens)
        assertEquals(List(14) { 0L }, usage.dailyTokens)
    }

    private fun codexUsage(timestamp: String, cumulative: Long, last: Long): String {
        return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count","info":{"last_token_usage":{"total_tokens":$last},"total_token_usage":{"total_tokens":$cumulative}}}}"""
    }

    private fun claudeUsage(
        timestamp: String,
        messageId: String,
        input: Long,
        output: Long,
        cacheCreation: Long,
        cacheRead: Long
    ): String {
        return """{"timestamp":"$timestamp","type":"assistant","message":{"id":"$messageId","usage":{"input_tokens":$input,"output_tokens":$output,"cache_creation_input_tokens":$cacheCreation,"cache_read_input_tokens":$cacheRead}}}"""
    }

    private fun session(providerId: String, source: File) = AgentSession(
        id = "$providerId:test",
        providerId = providerId,
        providerSessionId = "test",
        historyFilePath = source.absolutePath
    )

    private fun withHome(block: (File) -> Unit) {
        val home = Files.createTempDirectory("agentdock-token-test").toFile()
        try {
            block(home)
        } finally {
            home.deleteRecursively()
        }
    }
}
