package com.agentdock.storage

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class StateMigrationTest {
    @Test
    fun `cleans noisy session text from persisted project state`() {
        val state = AgentDockProjectState(
            sessions = mutableListOf(
                AgentSession(
                    id = "codex:019f4295-c4d2-7131-8775-706769a5b630",
                    providerId = CLIProvider.CODEX_ID,
                    providerSessionId = "019f4295-c4d2-7131-8775-706769a5b630",
                    name = "<environment_context> <cwd>/Users/manruixie/code/AgentDock</cwd>",
                    summary = "Imported from Codex local history."
                ),
                AgentSession(
                    id = "claude-code:a17b088f-5d37-41dc-89d8-522e849eaa4d",
                    providerId = CLIProvider.CLAUDE_CODE_ID,
                    providerSessionId = "a17b088f-5d37-41dc-89d8-522e849eaa4d",
                    name = "Claude Code session a17b088f",
                    summary = "Context: local shell\ncwd: /Users/manruixie/code/AgentDock\nshell: zsh\nFix the UI."
                )
            )
        )

        StateMigration.migrateProjectState(state)

        assertEquals("Codex session 019f4295", state.sessions[0].name)
        assertEquals("", state.sessions[0].summary)
        assertEquals("Claude Code session a17b088f", state.sessions[1].name)
        assertEquals("Fix the UI.", state.sessions[1].summary)
    }

    @Test
    fun `adds provider specific yolo templates to version one settings`() {
        val state = ProviderSettingsState(
            schemaVersion = 1,
            providers = CLIProvider.defaultProviders()
                .map { it.copy(yoloResumeCommandTemplate = "") }
                .toMutableList()
        )

        StateMigration.migrateProviderSettings(state)

        val defaults = CLIProvider.defaultProviders().associate { it.id to it.yoloResumeCommandTemplate }
        assertEquals(2, state.schemaVersion)
        assertEquals(defaults, state.providers.associate { it.id to it.yoloResumeCommandTemplate })
    }

    @Test
    fun `preserves intentionally disabled yolo template in current settings`() {
        val codex = CLIProvider.defaultProviders()
            .first { it.id == CLIProvider.CODEX_ID }
            .copy(yoloResumeCommandTemplate = "")
        val state = ProviderSettingsState(schemaVersion = 2, providers = mutableListOf(codex))

        StateMigration.migrateProviderSettings(state)

        assertEquals("", state.providers.first { it.id == CLIProvider.CODEX_ID }.yoloResumeCommandTemplate)
    }
}
