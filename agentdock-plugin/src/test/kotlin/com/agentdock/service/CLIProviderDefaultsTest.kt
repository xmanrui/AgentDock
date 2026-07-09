package com.agentdock.service

import com.agentdock.model.CLIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CLIProviderDefaultsTest {
    @Test
    fun `mvp defaults only include codex and claude code`() {
        assertEquals(
            listOf(CLIProvider.CODEX_ID, CLIProvider.CLAUDE_CODE_ID),
            CLIProvider.defaultProviders().map { it.id }
        )
    }

    @Test
    fun `default command templates use configured executable`() {
        val providers = CLIProvider.defaultProviders()

        assertTrue(providers.all { "{{executable}}" in it.startCommandTemplate })
        assertTrue(providers.all { "{{executable}}" in it.resumeCommandTemplate })
    }
}
