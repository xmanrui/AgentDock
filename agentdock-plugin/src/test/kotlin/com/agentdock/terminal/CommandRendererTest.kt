package com.agentdock.terminal

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderCommandContext
import com.agentdock.util.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandRendererTest {
    private val provider = CLIProvider.defaultProviders().first { it.id == CLIProvider.CODEX_ID }

    @Test
    fun `renders provider session id`() {
        val session = AgentSession(
            name = "Fix tests",
            providerId = provider.id,
            cwd = "/tmp/project",
            providerSessionId = "codex_123"
        )

        val result = CommandRenderer().render(
            "codex resume {{providerSessionId}}",
            context(session)
        )

        assertIs<CommandRenderResult.Success>(result)
        assertEquals("codex resume codex_123", result.command)
    }

    @Test
    fun `renders executable variable`() {
        val session = AgentSession(name = "Fix tests", providerId = provider.id, cwd = "/tmp/project")

        val result = CommandRenderer().render(
            "{{executable}} --version",
            context(session)
        )

        assertIs<CommandRenderResult.Success>(result)
        assertEquals("codex --version", result.command)
    }

    @Test
    fun `removes optional missing provider session id`() {
        val session = AgentSession(name = "Fix tests", providerId = provider.id, cwd = "/tmp/project")

        val result = CommandRenderer().render(
            "{{executable}} resume {{providerSessionId?}}",
            context(session)
        )

        assertIs<CommandRenderResult.Success>(result)
        assertEquals("codex resume", result.command)
    }

    @Test
    fun `fails when provider session id is required but missing`() {
        val session = AgentSession(name = "Fix tests", providerId = provider.id, cwd = "/tmp/project")

        val result = CommandRenderer().render(
            "codex resume {{providerSessionId}}",
            context(session)
        )

        assertIs<CommandRenderResult.MissingVariable>(result)
        assertEquals("providerSessionId", result.variable)
    }

    @Test
    fun `escapes variables with spaces`() {
        val session = AgentSession(
            name = "Fix quoted task",
            providerId = provider.id,
            cwd = "/tmp/project with spaces",
            providerSessionId = "codex_123"
        )

        val result = CommandRenderer().render("cd {{cwd}}", context(session))

        assertIs<CommandRenderResult.Success>(result)
        assertEquals("cd '/tmp/project with spaces'", result.command)
    }

    @Test
    fun `renders provider specific yolo resume commands`() {
        val expectedCommands = mapOf(
            CLIProvider.CODEX_ID to "codex resume --dangerously-bypass-approvals-and-sandbox session-123",
            CLIProvider.CLAUDE_CODE_ID to "claude --resume session-123 --ide --dangerously-skip-permissions",
            CLIProvider.GEMINI_ID to "gemini --resume session-123 --yolo"
        )

        CLIProvider.defaultProviders().forEach { currentProvider ->
            val session = AgentSession(
                name = "YOLO test",
                providerId = currentProvider.id,
                cwd = "/tmp/project",
                providerSessionId = "session-123"
            )
            val result = CommandRenderer().render(
                currentProvider.yoloResumeCommandTemplate,
                context(session, currentProvider)
            )

            assertIs<CommandRenderResult.Success>(result)
            assertEquals(expectedCommands.getValue(currentProvider.id), result.command)
        }
    }

    private fun context(session: AgentSession, currentProvider: CLIProvider = provider): ProviderCommandContext {
        return ProviderCommandContext(
            provider = currentProvider,
            session = session,
            projectPath = "/tmp/project",
            shell = "/bin/zsh",
            os = OperatingSystem.Mac
        )
    }
}
