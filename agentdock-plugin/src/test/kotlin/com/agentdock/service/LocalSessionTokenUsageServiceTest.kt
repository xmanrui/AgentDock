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
    fun `computes Codex token and response metrics as 7 day series`() = withHome { home ->
        val source = File(home, "codex.jsonl")
        source.writeText(
            listOf(
                "not-json",
                codexUsage("2026-07-01T10:00:00Z", cumulative = 100, last = 100),
                codexUsage("2026-07-02T10:00:00Z", cumulative = 150, last = 50),
                codexUsage("2026-07-02T10:00:01Z", cumulative = 150, last = 50),
                codexTaskComplete("2026-07-05T10:00:01Z", durationMillis = 1_000),
                codexTaskComplete("2026-07-10T09:00:02Z", durationMillis = 2_000),
                codexUsage("2026-07-10T10:00:00Z", cumulative = 30, last = 30),
                codexTurnAborted("2026-07-10T10:30:00Z", durationMillis = 90_000),
                codexTaskComplete("2026-07-10T11:00:04Z", durationMillis = 4_000)
            ).joinToString("\n")
        )
        val session = session(CLIProvider.CODEX_ID, source)
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)

        val usage = service.load(session)

        assertEquals(180L, usage.totalTokens)
        assertEquals(7, usage.dailyTokens.size)
        assertEquals(30L, usage.dailyTokens[5])
        assertEquals(30L, usage.dailyTokens.sum())
        assertEquals(listOf(1_000L, null, null, null, null, 3_000L, null), usage.dailyAverageResponseMillis)
        assertEquals(usage, service.cached(session))
        assertTrue(session.tokenUsageCached)
        assertEquals(180L, session.tokenUsageTotal)
        assertEquals(usage.dailyTokens, session.tokenUsageDaily)
        assertEquals(listOf(1_000L, -1L, -1L, -1L, -1L, 3_000L, -1L), session.averageResponseTimeDailyMillis)
        assertEquals(2, session.sessionMetricsCacheVersion)
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
    fun `aggregates cached token totals for one provider`() = withHome { home ->
        val firstSource = File(home, "codex-first.jsonl").apply {
            writeText(codexUsage("2026-07-10T10:00:00Z", cumulative = 90, last = 90))
        }
        val secondSource = File(home, "codex-second.jsonl").apply {
            writeText(codexUsage("2026-07-10T11:00:00Z", cumulative = 120, last = 120))
        }
        val claudeSource = File(home, "claude.jsonl").apply {
            writeText(claudeUsage("2026-07-10T12:00:00Z", "message", 3, 7, 0, 0))
        }
        val sessions = listOf(
            session(CLIProvider.CODEX_ID, firstSource),
            session(CLIProvider.CODEX_ID, secondSource),
            session(CLIProvider.CLAUDE_CODE_ID, claudeSource)
        )
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)
        sessions.forEach(service::load)

        assertEquals(210L, service.cachedProviderTotal(sessions, CLIProvider.CODEX_ID))
        assertEquals(10L, service.cachedProviderTotal(sessions, CLIProvider.CLAUDE_CODE_ID))
        assertNull(service.cachedProviderTotal(sessions, "unsupported"))
    }

    @Test
    fun `token usage cache survives project state serialization`() = withHome { home ->
        val source = File(home, "codex.jsonl")
        source.writeText(
            listOf(
                codexTaskComplete("2026-07-10T10:00:00Z", durationMillis = 3_000),
                codexUsage("2026-07-10T10:00:00Z", cumulative = 90, last = 90)
            ).joinToString("\n")
        )
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
                claudeUser("2026-07-05T09:59:58Z", "first prompt"),
                claudeUsage("2026-07-05T10:00:00Z", "message-a", 2, 10, 20, 30),
                claudeUsage("2026-07-05T10:00:01Z", "message-a", 2, 10, 20, 30),
                claudeTurnDuration("2026-07-05T10:00:02Z", durationMillis = 2_000),
                claudeUser("2026-07-06T09:59:55Z", "second prompt"),
                claudeUsage("2026-07-06T10:00:00Z", "message-a", 2, 15, 20, 30),
                claudeTurnDuration("2026-07-06T10:00:01Z", durationMillis = 5_000),
                claudeUser("2026-07-10T09:59:56Z", "third prompt"),
                claudeUsage("2026-07-10T10:00:00Z", "message-b", 3, 4, 5, 6),
                claudeTurnDuration("2026-07-10T10:00:01Z", durationMillis = 4_000)
            ).joinToString("\n")
        )
        val session = session(CLIProvider.CLAUDE_CODE_ID, source)
        val service = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)

        val usage = service.load(session)

        assertEquals(205L, usage.totalTokens)
        assertEquals(67L, usage.dailyTokens[1])
        assertEquals(18L, usage.dailyTokens[5])
        assertEquals(85L, usage.dailyTokens.sum())
        assertEquals(listOf(2_000L, 5_000L, null, null, null, 4_000L, null), usage.dailyAverageResponseMillis)
    }

    @Test
    fun `uses Claude turn duration and ignores unfinished turns`() = withHome { home ->
        val source = File(home, "claude.jsonl")
        source.writeText(
            listOf(
                claudeUser("2026-07-10T10:00:00Z", "real prompt"),
                claudeAssistant("2026-07-10T10:00:02Z"),
                claudeTurnDuration("2026-07-10T10:00:03Z", durationMillis = 2_000),
                claudeUser("2026-07-10T10:00:10Z", "unfinished prompt"),
                claudeAssistant("2026-07-10T10:00:11Z")
            ).joinToString("\n")
        )

        val usage = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)
            .load(session(CLIProvider.CLAUDE_CODE_ID, source))

        assertEquals(listOf(null, null, null, null, null, 2_000L, null), usage.dailyAverageResponseMillis)
    }

    @Test
    fun `computes Gemini token and response metrics`() = withHome { home ->
        val source = File(home, "gemini.json")
        source.writeText(
            """
            {
              "sessionId":"gemini-session",
              "messages":[
                {"type":"user","timestamp":"2026-07-10T10:00:00Z","content":"First prompt"},
                {"type":"gemini","timestamp":"2026-07-10T10:00:02Z","content":"First response","tokens":{"input":10,"output":20,"total":30}},
                {"type":"user","timestamp":"2026-07-10T11:00:00Z","content":"Second prompt"},
                {"type":"gemini","timestamp":"2026-07-10T11:00:04Z","content":"Second response","tokens":{"input":15,"output":25,"total":40}}
              ]
            }
            """.trimIndent()
        )

        val usage = LocalSessionTokenUsageService(LocalSessionContentService(home), clock)
            .load(session(CLIProvider.GEMINI_ID, source))

        assertEquals(70L, usage.totalTokens)
        assertEquals(70L, usage.dailyTokens[5])
        assertEquals(listOf(null, null, null, null, null, 3_000L, null), usage.dailyAverageResponseMillis)
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
        assertEquals(List(7) { 0L }, usage.dailyTokens)
        assertEquals(List(7) { null }, usage.dailyAverageResponseMillis)
    }

    private fun codexUsage(timestamp: String, cumulative: Long, last: Long): String {
        return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count","info":{"last_token_usage":{"total_tokens":$last},"total_token_usage":{"total_tokens":$cumulative}}}}"""
    }

    private fun codexTaskComplete(timestamp: String, durationMillis: Long): String {
        return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"task_complete","duration_ms":$durationMillis}}"""
    }

    private fun codexTurnAborted(timestamp: String, durationMillis: Long): String {
        return """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"turn_aborted","duration_ms":$durationMillis}}"""
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

    private fun claudeUser(timestamp: String, text: String): String {
        return """{"timestamp":"$timestamp","type":"user","message":{"role":"user","content":"$text"}}"""
    }

    private fun claudeAssistant(timestamp: String): String {
        return """{"timestamp":"$timestamp","type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"reply"}]}}"""
    }

    private fun claudeTurnDuration(timestamp: String, durationMillis: Long): String {
        return """{"timestamp":"$timestamp","type":"system","subtype":"turn_duration","durationMs":$durationMillis}"""
    }

    private fun session(providerId: String, source: File) = AgentSession(
        id = "$providerId:${source.nameWithoutExtension}",
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
