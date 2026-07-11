package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSessionContentServiceTest {
    @Test
    fun `loads Codex user and assistant messages without injected context`() = withHome { home ->
        val sessionId = "12345678-1234-1234-1234-123456789abc"
        val source = File(home, ".codex/sessions/2026/07/10/rollout-2026-07-10T10-00-00-$sessionId.jsonl")
        source.parentFile.mkdirs()
        source.writeText(
            listOf(
                """{"type":"session_meta","payload":{"id":"$sessionId"}}""",
                """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Build a hover preview.\n<environment_context>secret context</environment_context>\nKeep this line."}]}}""",
                """{"type":"response_item","payload":{"type":"function_call","name":"shell"}}""",
                """{"type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Implemented the preview."}]}}"""
            ).joinToString("\n")
        )

        val preview = LocalSessionContentService(home).load(session(CLIProvider.CODEX_ID, sessionId))

        assertEquals(2, preview.messages.size)
        assertEquals(SessionContentRole.User, preview.messages[0].role)
        assertEquals("Build a hover preview.\n\nKeep this line.", preview.messages[0].text)
        assertEquals(SessionContentRole.Assistant, preview.messages[1].role)
        assertEquals("Implemented the preview.", preview.messages[1].text)
        assertEquals(0, preview.omittedMessageCount)
        assertNull(preview.notice)
    }

    @Test
    fun `loads Claude Code text blocks and ignores metadata and tool results`() = withHome { home ->
        val sessionId = "claude-session"
        val projectPath = "/tmp/agentdock-project"
        val source = File(home, ".claude/projects/-tmp-agentdock-project/$sessionId.jsonl")
        source.parentFile.mkdirs()
        source.writeText(
            listOf(
                """{"type":"user","isMeta":true,"message":{"role":"user","content":"hidden metadata"}}""",
                """{"type":"user","message":{"role":"user","content":"Explain this project."}}""",
                """{"type":"assistant","message":{"role":"assistant","content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"It manages local sessions."},{"type":"tool_use","name":"Read"}]}}""",
                """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","content":"tool output"}]}}"""
            ).joinToString("\n")
        )

        val preview = LocalSessionContentService(home).load(
            session(CLIProvider.CLAUDE_CODE_ID, sessionId, projectPath)
        )

        assertEquals(
            listOf("Explain this project.", "It manages local sessions."),
            preview.messages.map { it.text }
        )
        assertEquals(listOf(SessionContentRole.User, SessionContentRole.Assistant), preview.messages.map { it.role })
        assertNull(preview.notice)
    }

    @Test
    fun `keeps the opening message and the nine most recent messages`() = withHome { home ->
        val sessionId = "12345678-1234-1234-1234-123456789abc"
        val source = File(home, ".codex/sessions/2026/07/10/rollout-$sessionId.jsonl")
        source.parentFile.mkdirs()
        source.writeText(
            (1..14).joinToString("\n") { index ->
                val role = if (index % 2 == 0) "assistant" else "user"
                """{"type":"response_item","payload":{"type":"message","role":"$role","content":[{"type":"text","text":"message $index"}]}}"""
            }
        )

        val preview = LocalSessionContentService(home).load(session(CLIProvider.CODEX_ID, sessionId))

        assertEquals(10, preview.messages.size)
        assertEquals("message 1", preview.messages.first().text)
        assertEquals((6..14).map { "message $it" }, preview.messages.drop(1).map { it.text })
        assertEquals(4, preview.omittedMessageCount)
    }

    @Test
    fun `loads Gemini CLI user and model messages`() = withHome { home ->
        val sessionId = "b77d543d-709c-40ba-b8da-7bb5b0f6767b"
        val projectPath = "/tmp/gemini-project"
        val source = File(home, "gemini-session.json")
        source.writeText(
            """
            {
              "sessionId":"$sessionId",
              "messages":[
                {"type":"info","content":"metadata"},
                {"type":"user","content":"Explain this module."},
                {"type":"gemini","content":"It manages project-scoped CLI sessions."},
                {"type":"error","content":"transient error"}
              ]
            }
            """.trimIndent()
        )

        val preview = LocalSessionContentService(home).load(
            session(CLIProvider.GEMINI_ID, sessionId, projectPath).copy(historyFilePath = source.absolutePath)
        )

        assertEquals(
            listOf("Explain this module.", "It manages project-scoped CLI sessions."),
            preview.messages.map { it.text }
        )
        assertEquals(listOf(SessionContentRole.User, SessionContentRole.Assistant), preview.messages.map { it.role })
        assertNull(preview.notice)
    }

    @Test
    fun `falls back to the stored summary when a history file is unavailable`() = withHome { home ->
        val preview = LocalSessionContentService(home).load(
            session(CLIProvider.CODEX_ID, "missing").copy(summary = "Fallback session purpose.")
        )

        assertEquals(listOf("Fallback session purpose."), preview.messages.map { it.text })
        assertTrue(preview.notice.orEmpty().contains("not available"))
    }

    private fun session(providerId: String, providerSessionId: String, projectPath: String = "/tmp/project") = AgentSession(
        id = "$providerId:$providerSessionId",
        projectPath = projectPath,
        name = "Session title",
        providerId = providerId,
        providerSessionId = providerSessionId
    )

    private fun withHome(block: (File) -> Unit) {
        val home = Files.createTempDirectory("agentdock-content-test").toFile()
        try {
            block(home)
        } finally {
            home.deleteRecursively()
        }
    }
}
