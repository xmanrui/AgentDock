package com.agentdock.storage

import com.agentdock.model.CLIProvider
import com.agentdock.util.SessionTextSanitizer

object StateMigration {
    private val supportedProviderIds = setOf(
        CLIProvider.CODEX_ID,
        CLIProvider.CLAUDE_CODE_ID,
        CLIProvider.GEMINI_ID
    )
    private val legacyTemplates = mapOf(
        CLIProvider.CODEX_ID to LegacyTemplates(
            start = "codex",
            resume = "codex resume {{providerSessionId}}"
        ),
        CLIProvider.CLAUDE_CODE_ID to LegacyTemplates(
            start = "claude",
            resume = "claude --resume {{providerSessionId}}"
        )
    )

    fun migrateProjectState(state: AgentDockProjectState): AgentDockProjectState {
        if (state.schemaVersion <= 0) {
            state.schemaVersion = 1
        }
        state.sessions.forEach { session ->
            if (SessionTextSanitizer.isNoisy(session.summary)) {
                session.summary = SessionTextSanitizer.summary(session.summary)
            }
            if (SessionTextSanitizer.isNoisy(session.name)) {
                session.name = fallbackSessionName(session.providerId, session.providerSessionId)
            }
        }
        return state
    }

    fun migrateProviderSettings(state: ProviderSettingsState): ProviderSettingsState {
        val needsYoloTemplateMigration = state.schemaVersion < 2
        val needsYoloStartTemplateMigration = state.schemaVersion < 3
        if (state.schemaVersion <= 0) {
            state.schemaVersion = 1
        }

        val existingIds = state.providers.map { it.id }.toSet()
        val defaults = CLIProvider.defaultProviders()
        defaults
            .filterNot { it.id in existingIds }
            .forEach { state.providers.add(it) }

        state.providers.removeAll { it.id !in supportedProviderIds }
        state.providers.forEach { provider ->
            val defaultsForProvider = defaults.firstOrNull { it.id == provider.id } ?: return@forEach
            legacyTemplates[provider.id]?.let { legacy ->
                if (provider.startCommandTemplate == legacy.start) {
                    provider.startCommandTemplate = defaultsForProvider.startCommandTemplate
                }
                if (provider.resumeCommandTemplate == legacy.resume) {
                    provider.resumeCommandTemplate = defaultsForProvider.resumeCommandTemplate
                }
            }
            if (needsYoloTemplateMigration && provider.yoloResumeCommandTemplate.isBlank()) {
                provider.yoloResumeCommandTemplate = defaultsForProvider.yoloResumeCommandTemplate
            }
            if (needsYoloStartTemplateMigration && provider.yoloStartCommandTemplate.isBlank()) {
                provider.yoloStartCommandTemplate = defaultsForProvider.yoloStartCommandTemplate
            }
        }
        if (state.newSessionProviderId !in supportedProviderIds) {
            state.newSessionProviderId = CLIProvider.CODEX_ID
            state.newSessionYolo = false
        }
        state.schemaVersion = maxOf(state.schemaVersion, 3)
        return state
    }

    private data class LegacyTemplates(
        val start: String,
        val resume: String
    )

    private fun fallbackSessionName(providerId: String, providerSessionId: String?): String {
        val suffix = providerSessionId?.take(8)?.takeIf { it.isNotBlank() }
        val providerName = when (providerId) {
            CLIProvider.CODEX_ID -> "Codex"
            CLIProvider.CLAUDE_CODE_ID -> "Claude Code"
            CLIProvider.GEMINI_ID -> "Gemini CLI"
            else -> "Agent"
        }
        return if (suffix == null) "$providerName session" else "$providerName session $suffix"
    }
}
