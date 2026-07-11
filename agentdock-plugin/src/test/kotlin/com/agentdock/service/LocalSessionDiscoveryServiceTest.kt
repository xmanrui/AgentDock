package com.agentdock.service

import com.agentdock.model.CLIProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.security.MessageDigest

class LocalSessionDiscoveryServiceTest {
    @Test
    fun `discovers codex sessions for current project cwd`() {
        val fixture = Fixture()
        val sessionId = "019f4295-c4d2-7131-8775-706769a5b630"
        fixture.writeCodexIndex(sessionId, "更新 AgentDock 插件", "2026-07-08T16:40:00Z")
        fixture.writeCodexSession(
            "2026/07/09/rollout-2026-07-09T00-35-39-$sessionId.jsonl",
            """
            {"timestamp":"2026-07-08T16:36:48Z","type":"session_meta","payload":{"id":"$sessionId","cwd":"${fixture.project.path}"}}
            {"timestamp":"2026-07-08T16:36:49Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"请更新 AgentDock 插件"}]}}
            """.trimIndent()
        )

        val sessions = fixture.discovery().discover(fixture.project.path)

        assertEquals(1, sessions.size)
        assertEquals("${CLIProvider.CODEX_ID}:$sessionId", sessions.single().id)
        assertEquals("更新 AgentDock 插件", sessions.single().name)
        assertEquals(CLIProvider.CODEX_ID, sessions.single().providerId)
        assertEquals(sessionId, sessions.single().providerSessionId)
        assertTrue(File(sessions.single().historyFilePath).isFile)
    }

    @Test
    fun `ignores codex sessions from other cwd even when text mentions project`() {
        val fixture = Fixture()
        val sessionId = "019f3dc4-caa4-7040-840a-1490c1188bc3"
        val otherProject = File(fixture.root, "other").apply { mkdirs() }
        fixture.writeCodexSession(
            "2026/07/08/rollout-2026-07-08T02-08-54-$sessionId.jsonl",
            """
            {"timestamp":"2026-07-07T18:09:00Z","type":"session_meta","payload":{"id":"$sessionId","cwd":"${otherProject.path}"}}
            {"timestamp":"2026-07-07T18:09:01Z","type":"response_item","payload":{"type":"message","role":"user","content":"AgentDock"}}
            """.trimIndent()
        )

        val sessions = fixture.discovery().discover(fixture.project.path)

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `discovers claude code project history`() {
        val fixture = Fixture()
        val sessionId = "a17b088f-5d37-41dc-89d8-522e849eaa4d"
        fixture.writeClaudeSession(
            sessionId,
            """
            {"type":"user","sessionId":"$sessionId","cwd":"${fixture.project.path}","timestamp":"2026-07-08T19:20:45Z","content":"完全不满足需求啊"}
            {"type":"assistant","sessionId":"$sessionId","cwd":"${fixture.project.path}","timestamp":"2026-07-08T19:20:50Z","message":{"type":"message","role":"assistant","content":[]}}
            """.trimIndent()
        )

        val sessions = fixture.discovery().discover(fixture.project.path)

        assertEquals(1, sessions.size)
        assertEquals("${CLIProvider.CLAUDE_CODE_ID}:$sessionId", sessions.single().id)
        assertEquals("完全不满足需求啊", sessions.single().name)
        assertEquals(CLIProvider.CLAUDE_CODE_ID, sessions.single().providerId)
        assertEquals(sessionId, sessions.single().providerSessionId)
        assertTrue(File(sessions.single().historyFilePath).isFile)
    }

    @Test
    fun `discovers claude code message content from current transcript format`() {
        val fixture = Fixture()
        val sessionId = "93a8b2df-131c-48de-a4ea-3bf164ebee5f"
        fixture.writeClaudeSession(
            sessionId,
            """
            {"type":"mode","mode":"normal","sessionId":"$sessionId"}
            {"parentUuid":null,"type":"user","message":{"role":"user","content":"台北明天天气"},"uuid":"b4c9d04b","timestamp":"2026-07-08T19:20:07Z","cwd":"${fixture.project.path}","sessionId":"$sessionId"}
            {"type":"assistant","sessionId":"$sessionId","cwd":"${fixture.project.path}","timestamp":"2026-07-08T19:20:08Z","message":{"type":"message","role":"assistant","content":[]}}
            """.trimIndent()
        )

        val session = fixture.discovery().discover(fixture.project.path).single()

        assertEquals("台北明天天气", session.name)
        assertEquals("台北明天天气", session.summary)
    }

    @Test
    fun `strips tool context from discovered codex summaries`() {
        val fixture = Fixture()
        val sessionId = "019f6000-c4d2-7131-8775-706769a5b630"
        fixture.writeCodexSession(
            "2026/07/09/rollout-2026-07-09T03-15-00-$sessionId.jsonl",
            """
            {"timestamp":"2026-07-09T03:15:00Z","type":"session_meta","payload":{"id":"$sessionId","cwd":"${fixture.project.path}"}}
            {"timestamp":"2026-07-09T03:15:01Z","type":"response_item","payload":{"type":"message","role":"user","content":"<environment_context>\n<cwd>${fixture.project.path}</cwd>\n<shell>zsh</shell>\n</environment_context>\nMatch the prototype page. Remove noisy metadata from cards."}}
            """.trimIndent()
        )

        val session = fixture.discovery().discover(fixture.project.path).single()

        assertEquals("Match the prototype page.", session.summary)
        assertEquals("Match the prototype page.", session.name)
        assertFalse(session.summary.contains("cwd", ignoreCase = true))
        assertFalse(session.summary.contains("shell", ignoreCase = true))
    }

    @Test
    fun `skips context only codex user messages`() {
        val fixture = Fixture()
        val sessionId = "019f7000-c4d2-7131-8775-706769a5b630"
        fixture.writeCodexSession(
            "2026/07/09/rollout-2026-07-09T03-25-00-$sessionId.jsonl",
            """
            {"timestamp":"2026-07-09T03:25:00Z","type":"session_meta","payload":{"id":"$sessionId","cwd":"${fixture.project.path}"}}
            {"timestamp":"2026-07-09T03:25:01Z","type":"response_item","payload":{"type":"message","role":"user","content":"<environment_context>\n<cwd>${fixture.project.path}</cwd>\n<shell>zsh</shell>\n</environment_context>"}}
            {"timestamp":"2026-07-09T03:25:02Z","type":"response_item","payload":{"type":"message","role":"user","content":"Show the current model id. Then stop."}}
            """.trimIndent()
        )

        val session = fixture.discovery().discover(fixture.project.path).single()

        assertEquals("Show the current model id.", session.summary)
        assertEquals("Show the current model id.", session.name)
    }

    @Test
    fun `strips tool context from discovered claude code summaries`() {
        val fixture = Fixture()
        val sessionId = "a17b088f-5d37-41dc-89d8-522e849eaa99"
        fixture.writeClaudeSession(
            sessionId,
            """
            {"type":"user","sessionId":"$sessionId","cwd":"${fixture.project.path}","timestamp":"2026-07-09T03:20:45Z","content":"Context: local shell\ncwd: ${fixture.project.path}\nshell: zsh\nMake AgentDock match the prototype. Keep the card readable."}
            """.trimIndent()
        )

        val session = fixture.discovery().discover(fixture.project.path).single()

        assertEquals("Make AgentDock match the prototype.", session.summary)
        assertEquals("Make AgentDock match the prototype.", session.name)
        assertFalse(session.summary.contains("cwd", ignoreCase = true))
        assertFalse(session.summary.contains("shell", ignoreCase = true))
    }

    @Test
    fun `discovers project scoped gemini cli sessions`() {
        val fixture = Fixture()
        val sessionId = "b77d543d-709c-40ba-b8da-7bb5b0f6767b"
        fixture.writeGeminiSession(
            sessionId,
            """
            {
              "sessionId":"$sessionId",
              "projectHash":"${fixture.projectHash()}",
              "startTime":"2026-07-11T15:13:05Z",
              "lastUpdated":"2026-07-11T15:14:48Z",
              "messages":[
                {"id":"user-1","timestamp":"2026-07-11T15:13:10Z","type":"user","content":"Add Gemini CLI support."},
                {"id":"model-1","timestamp":"2026-07-11T15:13:12Z","type":"gemini","content":"I will inspect the provider architecture."}
              ]
            }
            """.trimIndent()
        )

        val session = fixture.discovery().discover(fixture.project.path).single()

        assertEquals("${CLIProvider.GEMINI_ID}:$sessionId", session.id)
        assertEquals(CLIProvider.GEMINI_ID, session.providerId)
        assertEquals("Add Gemini CLI support.", session.name)
        assertEquals("Add Gemini CLI support.", session.summary)
        assertEquals(fixture.project.path, session.cwd)
        assertTrue(File(session.historyFilePath).isFile)
    }

    private class Fixture {
        val root: File = createTempDirectory("agentdock-discovery").toFile()
        val userHome: File = File(root, "home").apply { mkdirs() }
        val project: File = File(root, "AgentDock").apply { mkdirs() }

        fun discovery(): LocalSessionDiscoveryService = LocalSessionDiscoveryService(userHome)

        fun writeCodexIndex(id: String, threadName: String, updatedAt: String) {
            val index = File(userHome, ".codex/session_index.jsonl")
            index.parentFile.mkdirs()
            index.appendText("""{"id":"$id","thread_name":"$threadName","updated_at":"$updatedAt"}""" + "\n")
        }

        fun writeCodexSession(relativePath: String, content: String) {
            val file = File(userHome, ".codex/sessions/$relativePath")
            file.parentFile.mkdirs()
            file.writeText(content.trim() + "\n")
        }

        fun writeClaudeSession(sessionId: String, content: String) {
            val file = File(userHome, ".claude/projects/${project.path.replace(File.separatorChar, '-')}/$sessionId.jsonl")
            file.parentFile.mkdirs()
            file.writeText(content.trim() + "\n")
        }

        fun writeGeminiSession(sessionId: String, content: String) {
            val file = File(userHome, ".gemini/tmp/${projectHash()}/chats/session-test-${sessionId.take(8)}.json")
            file.parentFile.mkdirs()
            file.writeText(content.trim() + "\n")
        }

        fun projectHash(): String = MessageDigest.getInstance("SHA-256")
            .digest(project.absolutePath.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
