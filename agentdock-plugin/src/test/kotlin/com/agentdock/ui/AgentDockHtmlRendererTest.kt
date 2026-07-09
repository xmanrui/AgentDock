package com.agentdock.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AgentDockHtmlRendererTest {
    @Test
    fun `renders prototype-style sessions without noisy metadata or extra card actions`() {
        val html = AgentDockHtmlRenderer.render(
            initialState = AgentDockHtmlRenderer.ViewState(
                count = 1,
                health = "Codex ready · Claude Code ready",
                sessions = listOf(
                    AgentDockHtmlRenderer.SessionItem(
                        id = "codex:abc",
                        providerId = "codex",
                        providerName = "Codex",
                        title = "Match the prototype page.",
                        summary = "Match the prototype page.",
                        statusKey = "restorable",
                        statusLabel = "Restorable",
                        updatedLabel = "2分钟前",
                        pinned = false,
                        archived = false
                    )
                )
            ),
            bridgeScript = "window.__agentdockMessage = message;"
        )

        assertContains(html, "session-card")
        assertContains(html, "Search sessions")
        assertContains(html, "从当前文件")
        assertContains(html, "Open")
        assertContains(html, "Pin")
        assertContains(html, "Archive")
        assertFalse(html.contains("cwd", ignoreCase = true))
        assertFalse(html.contains("shell", ignoreCase = true))
        assertFalse(html.contains("Context", ignoreCase = true))
        assertFalse(html.contains("Rename"))
        assertFalse(html.contains("Refresh"))
        assertFalse(html.contains("Providers"))
        assertFalse(html.contains("Import Local Sessions"))
        assertFalse(html.contains("sessions-head"))
        assertFalse(html.contains("<span>Sessions</span>"))
        assertFalse(html.contains("<span>AgentDock</span>"))
        assertFalse(html.contains("data-action=\"new\""))
        assertFalse(html.contains("Provider settings"))
    }
}
