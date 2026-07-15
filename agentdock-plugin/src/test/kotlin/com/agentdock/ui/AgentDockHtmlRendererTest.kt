package com.agentdock.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentDockHtmlRendererTest {
    @Test
    fun `renders prototype-style sessions without noisy metadata or extra card actions`() {
        val html = AgentDockHtmlRenderer.render(
            initialState = AgentDockHtmlRenderer.ViewState(
                count = 1,
                providers = listOf(
                    AgentDockHtmlRenderer.ProviderItem("codex", "Codex"),
                    AgentDockHtmlRenderer.ProviderItem("claude-code", "Claude Code"),
                    AgentDockHtmlRenderer.ProviderItem("gemini", "Gemini CLI")
                ),
                sessions = listOf(
                    AgentDockHtmlRenderer.SessionItem(
                        id = "codex:abc",
                        providerId = "codex",
                        providerName = "Codex",
                        title = "Match the prototype page.",
                        summary = "Match the prototype page.",
                        totalTokens = 4_900_000,
                        dailyTokens = listOf(16, 9, 22, 12, 8, 20, 14),
                        dailyAverageResponseMillis = listOf(null, null, null, 2_100, null, null, null),
                        statusKey = "restorable",
                        statusLabel = "Restorable",
                        terminalOpen = false,
                        updatedLabel = "2分钟前",
                        pinned = false,
                        archived = false
                    )
                )
            ),
            bridgeScript = "window.__agentdockMessage = message;"
        )

        assertContains(html, "session-card")
        assertContains(html, "data-session-id")
        assertContains(html, "data-session-preview-id")
        assertContains(html, "session-name[data-session-preview-id]")
        assertContains(html, "title.closest")
        assertContains(html, "scheduleSessionPreview")
        assertContains(html, "hideSessionPreview")
        assertContains(html, "preview-show")
        assertContains(html, "preview-hide")
        assertContains(html, "getBoundingClientRect")
        assertContains(html, "payload.preserveView")
        assertContains(html, "}, 120)")
        assertContains(html, "terminalOpen")
        assertContains(html, "renderTerminalIndicator")
        assertContains(html, ".terminal-indicator")
        assertContains(html, "Terminal closed")
        assertContains(html, "terminal-indicator' + active")
        assertContains(html, "agentdock-terminal-pulse")
        assertContains(html, "prefers-reduced-motion")
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
        assertContains(html, "\"id\":\"gemini\"")
        assertContains(html, "providerId === \"gemini\"")
        assertContains(html, "Search sessions")
        assertContains(html, "new-session-launcher")
        assertContains(html, "data-new-session-default=\"true\"")
        assertContains(html, "data-hint-placement=\"below\"")
        assertContains(html, "data-hint-kind=\"action\"")
        assertContains(html, "Start a new ")
        assertContains(html, "newSessionDefault.addEventListener(\"mouseenter\"")
        assertContains(html, "newSessionDefault.addEventListener(\"focus\"")
        assertContains(html, ".agentdock-tooltip.action-hint")
        assertContains(html, ".agentdock-tooltip.below.show")
        assertContains(html, "data-new-session-menu-toggle=\"true\"")
        assertContains(html, "data-new-session-provider=\"")
        assertContains(html, "data-new-session-yolo=\"false\"")
        assertContains(html, "data-new-session-yolo=\"true\"")
        assertContains(html, "send(\"new-default\", {})")
        assertContains(html, "send(\"new\", {providerId: providerId, yolo: Boolean(yolo)})")
        assertContains(html, "setNewSessionMenuOpen")
        assertContains(html, "function closeNewSessionMenu()")
        assertContains(html, "closeNewSessionMenu: closeNewSessionMenu")
        assertContains(html, "window.addEventListener(\"blur\", closeNewSessionMenu)")
        assertContains(html, "if (!launcher) closeNewSessionMenu()")
        assertContains(html, "event.stopPropagation()")
        assertContains(html, "width: min(276px, calc(100vw - 20px))")
        assertContains(html, "grid-template-columns: minmax(0, 1fr) 82px")
        assertContains(html, "margin-left: 2px")
        assertContains(html, "border: 1px solid rgba(168, 178, 163, .28)")
        assertContains(html, "background: rgba(255, 255, 255, .035)")
        assertFalse(html.contains(">Standard<"))
        assertFalse(html.contains("confirm("))
        assertContains(html, "--content-horizontal-padding: 10px")
        assertContains(html, "padding: 8px var(--content-horizontal-padding)")
        assertContains(html, "padding: 7px var(--content-horizontal-padding)")
        assertContains(html, "padding: 9px var(--content-horizontal-padding) 16px")
        assertFalse(html.contains("border-left: 1px solid var(--line-soft)"))
        assertContains(html, "compositionstart")
        assertContains(html, "compositionend")
        assertContains(html, "event.isComposing")
        assertContains(html, "focusSearch: true")
        assertContains(html, "provider-filter-set")
        assertContains(html, "data-provider-usage")
        assertFalse(html.contains("agentdock-provider-usage"))
        assertFalse(html.contains("provider-usage-popover"))
        assertContains(html, "showProviderUsage")
        assertContains(html, "hideProviderUsage")
        assertContains(html, "provider-usage-show")
        assertContains(html, "provider-usage-hide")
        assertContains(html, "left: Math.round(rect.left)")
        assertContains(html, "top: Math.round(rect.top)")
        assertContains(html, "usePointer: Boolean(usePointer)")
        assertFalse(html.contains("receiveProviderUsage"))
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
        assertContains(html, ">Resume</button>")
        assertContains(html, "Resume in YOLO mode")
        assertContains(html, "Resume session in YOLO mode")
        assertContains(html, "YOLO")
        assertContains(html, "data-action=\"open-yolo\"")
        assertContains(html, "bypasses permission checks")
        assertContains(html, "Pin")
        assertContains(html, "session-time")
        assertContains(html, "Token Usage")
        assertContains(html, "Avg. Time")
        assertFalse(html.contains("Token 用量"))
        assertFalse(html.contains("平均响应"))
        assertContains(html, "session-metrics")
        assertContains(html, "session-token-usage")
        assertContains(html, "session-response-time")
        assertContains(html, "session-response-today")
        assertContains(html, "renderSessionMetrics")
        assertContains(html, "renderTokenTrend")
        assertContains(html, "renderResponseTrend")
        assertContains(html, "renderMetricTrend")
        assertContains(html, "7-day token usage")
        assertContains(html, "7-day average response time")
        assertContains(html, "Historical total token usage")
        assertContains(html, "formatTokenCount")
        assertContains(html, "formatResponseTime")
        assertContains(html, "seconds.toFixed(1).replace(/\\.0$/")
        assertContains(html, "+ \"S\"")
        assertFalse(html.contains("+ \"ms\""))
        assertContains(html, "Today's average response time")
        assertContains(html, "normalizedDailyResponseMillis")
        assertContains(html, "token-trend-marker")
        assertContains(html, ".token-trend-marker.missing")
        assertContains(html, "valuesWithData")
        assertContains(html, "point.hasData ? \"\" : \" missing\"")
        assertContains(html, "var values = dailyValues")
        assertContains(html, "data-point-count")
        assertContains(html, "var markers = available ? points.map")
        assertContains(html, "tokenTrendGradientId")
        assertContains(html, "tokenTrendCurveSegments")
        assertContains(html, "midpointX")
        assertContains(html, "data-segment-count")
        assertContains(html, "<path class=\"token-trend-line\"")
        assertContains(html, "token-trend-area")
        assertContains(html, "linearGradient")
        assertContains(html, "stop-opacity=\"0.32\"")
        assertContains(html, "renderTokenTrend(values, available, item.id)")
        assertContains(html, "stroke-width: 3")
        assertContains(html, "grid-template-columns: repeat(2, minmax(0, 1fr))")
        assertContains(html, "max-width: 180px")
        assertContains(html, "height: 48px")
        assertContains(html, "--trend-green: #4bde80")
        assertContains(html, "horizontalPadding = 3.25")
        assertContains(html, "width: 4px")
        assertFalse(html.contains("width: 6px"))
        assertFalse(html.contains("tokenTrendBuckets"))
        assertFalse(html.contains("index += 2"))
        assertFalse(html.contains("markerIndexes = [1, 2, 3, 5]"))
        assertFalse(html.contains("markerCandidates"))
        assertFalse(html.contains("<polyline class=\"token-trend-line\""))
        assertFalse(html.contains("token-trend-baseline"))
        assertContains(html, "\"totalTokens\":4900000")
        assertContains(html, "\"dailyTokens\":[16,9,22,12,8,20,14]")
        assertContains(html, "\"dailyAverageResponseMillis\":[null,null,null,2100,null,null,null]")
        assertFalse(html.contains("class=\"session-summary\""))
        assertFalse(html.contains(".session-summary"))
        assertFalse(html.contains("session-meta"))
        assertFalse(html.contains("providerName) + ' · '"))
        assertFalse(html.contains("data-status"))
        assertFalse(html.contains("statusClass"))
        assertFalse(html.contains("escapeHtml(item.statusLabel) + '</span>'"))
        assertFalse(html.contains("class=\"status "))
        assertFalse(html.contains("if (!item.terminalOpen) return \"\";"))
        assertFalse(html.contains("cwd", ignoreCase = true))
        assertFalse(html.contains("shell", ignoreCase = true))
        assertFalse(html.contains("Context", ignoreCase = true))
        assertFalse(html.contains("Rename"))
        assertFalse(html.contains("Refresh"))
        assertFalse(html.contains("Import Local Sessions"))
        assertFalse(html.contains("sessions-head"))
        assertFalse(html.contains("<span>Sessions</span>"))
        assertFalse(html.contains("<span>AgentDock</span>"))
        assertFalse(html.contains("Provider settings"))
        assertFalse(html.contains("Archive"))
        assertFalse(html.contains("Unarchive"))
        assertFalse(html.contains("data-action=\"archive\""))
        assertFalse(html.contains("root.querySelectorAll(\".session-card[data-session-id]\")"))
        assertFalse(html.contains("class=\"session-name\" title="))
        assertFalse(html.contains("从当前文件"))
        assertFalse(html.contains("data-action=\"current-file\""))
        assertFalse(html.contains("footer-strip"))
        assertFalse(html.contains("footer-refresh"))
        assertFalse(html.contains("Codex ready"))
        assertFalse(html.contains("Claude Code ready"))
        assertFalse(html.contains("minmax(0, 1fr) 44px"))
        assertTrue(html.indexOf("data-action=\"open\"") < html.indexOf("data-action=\"open-yolo\""))
        assertTrue(html.indexOf("data-action=\"open-yolo\"") < html.indexOf("data-action=\"pin\""))
    }

    @Test
    fun `renders the persisted yolo provider as the direct launch default`() {
        val html = AgentDockHtmlRenderer.render(
            initialState = AgentDockHtmlRenderer.ViewState(
                count = 0,
                providers = listOf(
                    AgentDockHtmlRenderer.ProviderItem("codex", "Codex"),
                    AgentDockHtmlRenderer.ProviderItem("claude-code", "Claude Code")
                ),
                sessions = emptyList(),
                defaultNewSessionProviderId = "claude-code",
                defaultNewSessionYolo = true
            ),
            bridgeScript = "window.__agentdockMessage = message;"
        )

        assertContains(html, "\"defaultNewSessionProviderId\":\"claude-code\"")
        assertContains(html, "\"defaultNewSessionYolo\":true")
        assertContains(html, "new-session-default-yolo")
        assertContains(html, "selectedYolo ? '<span class=\"new-session-default-yolo\">YOLO</span>'")
    }
}
