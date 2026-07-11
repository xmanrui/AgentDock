package com.agentdock.service

import com.agentdock.model.CLIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CLIProviderDefaultsTest {
    @Test
    fun `defaults include codex claude code and gemini cli`() {
        assertEquals(
            listOf(CLIProvider.CODEX_ID, CLIProvider.CLAUDE_CODE_ID, CLIProvider.GEMINI_ID),
            CLIProvider.defaultProviders().map { it.id }
        )
    }

    @Test
    fun `default command templates use configured executable`() {
        val providers = CLIProvider.defaultProviders()

        assertTrue(providers.all { "{{executable}}" in it.startCommandTemplate })
        assertTrue(providers.all { "{{executable}}" in it.resumeCommandTemplate })
        assertTrue(providers.all { "{{executable}}" in it.yoloResumeCommandTemplate })
    }

    @Test
    fun `default yolo templates use each cli permission bypass flag`() {
        val providers = CLIProvider.defaultProviders().associateBy { it.id }

        assertEquals(
            "{{executable}} resume --dangerously-bypass-approvals-and-sandbox {{providerSessionId?}}",
            providers.getValue(CLIProvider.CODEX_ID).yoloResumeCommandTemplate
        )
        assertEquals(
            "{{executable}} --resume {{providerSessionId?}} --ide --dangerously-skip-permissions",
            providers.getValue(CLIProvider.CLAUDE_CODE_ID).yoloResumeCommandTemplate
        )
        assertEquals(
            "{{executable}} --resume {{providerSessionId?}} --yolo",
            providers.getValue(CLIProvider.GEMINI_ID).yoloResumeCommandTemplate
        )
    }
}
