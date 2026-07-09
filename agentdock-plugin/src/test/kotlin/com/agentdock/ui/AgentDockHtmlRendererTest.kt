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
                providers = listOf(
                    AgentDockHtmlRenderer.ProviderItem("codex", "Codex"),
                    AgentDockHtmlRenderer.ProviderItem("claude-code", "Claude Code")
                ),
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
        assertContains(html, "provider-filters")
        assertContains(html, "data-provider=\"all\"")
        assertContains(html, "provider-count")
        assertContains(html, "agentdock-tooltip")
        assertContains(html, "data-hint")
        assertContains(html, "showSessionHint")
        assertContains(html, "hideSessionHint")
        assertContains(html, "\"count\":1")
        assertContains(html, "This project has ")
        assertContains(html, "session.")
        assertContains(html, "sessions.")
        assertContains(html, "All AI providers")
        assertContains(html, "\"id\":\"codex\"")
        assertContains(html, "\"id\":\"claude-code\"")
        assertContains(html, "Search sessions")
        assertContains(html, "compositionstart")
        assertContains(html, "compositionend")
        assertContains(html, "event.isComposing")
        assertContains(html, "focusSearch: true")
        assertContains(html, "provider-filter-set")
        assertContains(html, "filter-refresh")
        assertContains(html, "data-action=\"refresh\"")
        assertContains(html, "is-refreshing")
        assertContains(html, "aria-busy")
        assertContains(html, "agentdock-refresh-spin")
        assertContains(html, "beginReloadFeedback")
        assertContains(html, "finishReloadFeedback")
        assertContains(html, "payload.refreshPending")
        assertContains(html, "刷新会话")
        assertContains(html, "if (composingSearch)")
        assertContains(html, "Open")
        assertContains(html, "Pin")
        assertContains(html, "Archive")
        assertContains(html, "session-time")
        assertFalse(html.contains("session-meta"))
        assertFalse(html.contains("providerName) + ' · '"))
        assertFalse(html.contains("data-status"))
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
        assertFalse(html.contains("从当前文件"))
        assertFalse(html.contains("data-action=\"current-file\""))
        assertFalse(html.contains("footer-strip"))
        assertFalse(html.contains("footer-refresh"))
        assertFalse(html.contains("Codex ready"))
        assertFalse(html.contains("Claude Code ready"))
        assertFalse(html.contains("minmax(0, 1fr) 44px"))
    }
}
