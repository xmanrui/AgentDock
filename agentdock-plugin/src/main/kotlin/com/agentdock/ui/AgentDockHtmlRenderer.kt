package com.agentdock.ui

import com.google.gson.Gson

object AgentDockHtmlRenderer {
    private val gson = Gson()

    data class ViewState(
        val sessions: List<SessionItem>,
        val providers: List<ProviderItem> = emptyList(),
        val count: Int
    )

    data class ProviderItem(
        val id: String,
        val name: String
    )

    data class SessionItem(
        val id: String,
        val providerId: String,
        val providerName: String,
        val title: String,
        val summary: String,
        val totalTokens: Long? = null,
        val dailyTokens: List<Long> = emptyList(),
        val dailyAverageResponseMillis: List<Long?> = emptyList(),
        val statusKey: String,
        val statusLabel: String,
        val terminalOpen: Boolean = false,
        val updatedLabel: String,
        val pinned: Boolean,
        val archived: Boolean
    )

    data class ActionResponse(
        val state: ViewState? = null,
        val query: String? = null,
        val error: String? = null,
        val refreshPending: Boolean = false,
        val preserveView: Boolean = false
    )

    fun interactionHandledResponseJson(): String = gson.toJson(ActionResponse(preserveView = true))

    fun render(initialState: ViewState, bridgeScript: String): String {
        val stateJson = scriptJson(initialState)
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                :root {
                  --bg: #1b1f1b;
                  --chrome: #212520;
                  --panel: #20241f;
                  --panel-2: #252a24;
                  --panel-3: #171a17;
                  --line: #343b35;
                  --line-soft: #29302a;
                  --text: #eef2ec;
                  --text-soft: #c0c8bf;
                  --text-dim: #889287;
                  --green: #68d982;
                  --green-soft: rgba(104, 217, 130, .13);
                  --trend-green: #4bde80;
                  --blue: #73a7ff;
                  --blue-soft: rgba(115, 167, 255, .14);
                  --orange: #d97757;
                  --yellow: #e8b75d;
                  --red: #f06b73;
                  --radius: 8px;
                  --content-horizontal-padding: 10px;
                  --sans: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                }

                * { box-sizing: border-box; }
                html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; }
                body {
                  color: var(--text);
                  background: var(--bg);
                  font-family: var(--sans);
                  font-size: 13px;
                  letter-spacing: 0;
                }
                button, input { font: inherit; letter-spacing: 0; }

                .agentdock {
                  width: 100%;
                  height: 100vh;
                  display: grid;
                  grid-template-rows: 48px 42px minmax(0, 1fr);
                  background: rgba(27, 31, 27, .98);
                }

                .plain-button,
                .provider-filter,
                .session-action {
                  border: 1px solid transparent;
                  border-radius: 6px;
                  color: var(--text-soft);
                  background: transparent;
                  cursor: pointer;
                  transition: background .15s ease, border-color .15s ease, color .15s ease, transform .15s ease;
                }

                .plain-button:hover,
                .provider-filter:hover,
                .session-action:hover {
                  color: var(--text);
                  background: rgba(255, 255, 255, .07);
                  border-color: var(--line);
                }

                .search-row {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr);
                  gap: 8px;
                  align-items: center;
                  padding: 8px var(--content-horizontal-padding);
                  border-bottom: 1px solid var(--line-soft);
                }

                .search {
                  width: 100%;
                  height: 32px;
                  min-width: 0;
                  border: 1px solid var(--line);
                  border-radius: 7px;
                  background: rgba(12, 15, 12, .58);
                  color: var(--text);
                  padding: 0 10px;
                  outline: none;
                }

                .search::placeholder { color: var(--text-dim); }
                .search:focus {
                  border-color: rgba(115, 167, 255, .62);
                  box-shadow: 0 0 0 2px rgba(115, 167, 255, .13);
                }

                .plain-button {
                  height: 32px;
                  padding: 0 10px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  gap: 7px;
                  background: rgba(255, 255, 255, .045);
                  border-color: var(--line);
                  font-weight: 700;
                  white-space: nowrap;
                }

                .session-action.yolo {
                  color: var(--yellow);
                  border-color: rgba(232, 183, 93, .32);
                  background: rgba(232, 183, 93, .08);
                }

                .session-action.yolo:hover {
                  color: #ffe0a0;
                  border-color: rgba(232, 183, 93, .52);
                  background: rgba(232, 183, 93, .14);
                }

                .provider-filters {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) auto;
                  align-items: center;
                  gap: 8px;
                  padding: 7px var(--content-horizontal-padding);
                  border-bottom: 1px solid var(--line-soft);
                }

                .provider-filter-set {
                  min-width: 0;
                  display: flex;
                  align-items: center;
                  gap: 6px;
                  overflow-x: auto;
                  overflow-y: hidden;
                }

                .provider-filter-set::-webkit-scrollbar { display: none; }

                .provider-filter {
                  height: 28px;
                  min-width: 36px;
                  padding: 0 8px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  background: rgba(255, 255, 255, .035);
                  border-color: var(--line-soft);
                  flex-shrink: 0;
                }

                .provider-filter.all {
                  min-width: 58px;
                  gap: 6px;
                  padding: 0 7px 0 9px;
                  font-size: 12px;
                  font-weight: 760;
                  white-space: nowrap;
                }

                .provider-filter.active {
                  color: #dfeaff;
                  background: var(--blue-soft);
                  border-color: rgba(115, 167, 255, .42);
                }

                .provider-count {
                  min-width: 18px;
                  height: 18px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  padding: 0 5px;
                  border: 1px solid rgba(255, 255, 255, .08);
                  border-radius: 999px;
                  color: var(--text-soft);
                  background: rgba(255, 255, 255, .07);
                  font-size: 11px;
                  font-weight: 820;
                  line-height: 1;
                }

                .provider-filter.all.active .provider-count {
                  color: #dfeaff;
                  background: rgba(115, 167, 255, .18);
                  border-color: rgba(115, 167, 255, .25);
                }

                .agentdock-tooltip {
                  position: fixed;
                  z-index: 30;
                  left: 0;
                  top: 0;
                  max-width: calc(100vw - 16px);
                  padding: 7px 9px;
                  border: 1px solid var(--line);
                  border-radius: 7px;
                  color: var(--text);
                  background: rgba(23, 26, 23, .98);
                  box-shadow: 0 14px 38px rgba(0, 0, 0, .38);
                  font-size: 12px;
                  line-height: 1.35;
                  white-space: nowrap;
                  pointer-events: none;
                  opacity: 0;
                  transform: translate(-50%, calc(-100% - 4px));
                  transition: opacity .12s ease, transform .12s ease;
                }

                .agentdock-tooltip.show {
                  opacity: 1;
                  transform: translate(-50%, calc(-100% - 8px));
                }

                .provider-filter .logo {
                  width: 19px;
                  height: 19px;
                  margin: 0;
                  display: block;
                }

                .sessions-list {
                  min-height: 0;
                  overflow: auto;
                  padding: 9px var(--content-horizontal-padding) 16px;
                  display: flex;
                  flex-direction: column;
                  gap: 8px;
                }

                .session-card {
                  border: 1px solid var(--line-soft);
                  border-radius: var(--radius);
                  background: rgba(255, 255, 255, .032);
                  padding: 9px 10px;
                  cursor: default;
                  transition: background .15s ease, border-color .15s ease, transform .15s ease;
                }

                .session-card:hover {
                  background: rgba(255, 255, 255, .055);
                  border-color: var(--line);
                  transform: translateY(-1px);
                }

                .session-top {
                  display: grid;
                  grid-template-columns: 24px minmax(0, 1fr) auto;
                  gap: 9px;
                  align-items: center;
                }

                .logo {
                  width: 20px;
                  height: 20px;
                  margin-top: 1px;
                  flex: 0 0 20px;
                  display: inline-block;
                }

                .session-name {
                  min-width: 0;
                  color: var(--text);
                  font-weight: 770;
                  line-height: 1.25;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }

                .terminal-indicator {
                  width: 18px;
                  height: 18px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  border-radius: 999px;
                }

                .terminal-indicator::before {
                  content: "";
                  width: 8px;
                  height: 8px;
                  display: block;
                  border-radius: 999px;
                  background: #69736b;
                  box-shadow: 0 0 0 3px rgba(255, 255, 255, .045);
                }

                .terminal-indicator.active::before {
                  background: var(--green);
                  box-shadow: 0 0 0 3px var(--green-soft);
                  animation: agentdock-terminal-pulse 1.35s ease-in-out infinite;
                }

                @keyframes agentdock-terminal-pulse {
                  0%, 100% {
                    transform: scale(.96);
                    box-shadow: 0 0 0 3px var(--green-soft);
                  }
                  50% {
                    transform: scale(1.1);
                    box-shadow: 0 0 0 5px rgba(104, 217, 130, .22);
                  }
                }

                @media (prefers-reduced-motion: reduce) {
                  .terminal-indicator.active::before {
                    animation: none;
                  }
                }

                .session-metrics {
                  min-height: 48px;
                  margin-top: 7px;
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  align-items: center;
                  gap: 12px;
                }

                .session-token-usage,
                .session-response-time {
                  min-width: 0;
                  height: 48px;
                  display: grid;
                  grid-template-columns: auto minmax(32px, 1fr) minmax(40px, auto);
                  align-items: center;
                  gap: 6px;
                }

                .session-token-label,
                .session-response-label {
                  color: var(--text-dim);
                  font-size: 11px;
                  line-height: 1;
                  white-space: nowrap;
                }

                .session-token-chart,
                .session-response-chart {
                  width: 100%;
                  max-width: 180px;
                  min-width: 0;
                  height: 48px;
                  position: relative;
                  justify-self: end;
                }

                .session-token-chart svg,
                .session-response-chart svg {
                  width: 100%;
                  height: 48px;
                  display: block;
                  overflow: visible;
                  position: absolute;
                  inset: 0;
                }

                .token-trend-line {
                  fill: none;
                  stroke: var(--trend-green);
                  stroke-width: 3;
                  stroke-linecap: round;
                  stroke-linejoin: round;
                  vector-effect: non-scaling-stroke;
                }

                .token-trend-area {
                  stroke: none;
                  pointer-events: none;
                }

                .token-trend-marker {
                  width: 4px;
                  height: 4px;
                  position: absolute;
                  display: block;
                  border-radius: 50%;
                  background: var(--trend-green);
                  transform: translate(-50%, -50%);
                  pointer-events: none;
                }

                .token-trend-marker.missing {
                  background: var(--text-dim);
                  opacity: .35;
                }

                .session-token-chart.unavailable .token-trend-line,
                .session-response-chart.unavailable .token-trend-line {
                  stroke: var(--text-dim);
                  stroke-dasharray: 3 3;
                  opacity: .5;
                }

                .session-token-total,
                .session-response-today {
                  min-width: 40px;
                  color: var(--text);
                  font-size: 12px;
                  font-weight: 760;
                  line-height: 1;
                  text-align: right;
                  white-space: nowrap;
                  font-variant-numeric: tabular-nums;
                }

                .session-footer {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) auto;
                  align-items: center;
                  gap: 8px;
                  margin-top: 8px;
                }

                @media (max-width: 300px) {
                  .session-metrics {
                    gap: 7px;
                  }

                  .session-token-usage,
                  .session-response-time {
                    grid-template-columns: auto minmax(24px, 1fr) auto;
                    gap: 4px;
                  }

                  .session-token-label,
                  .session-response-label {
                    font-size: 10px;
                  }
                }

                .session-time {
                  min-width: 0;
                  color: var(--text-dim);
                  font-size: 11px;
                  line-height: 1.2;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }

                .session-actions {
                  display: flex;
                  align-items: center;
                  justify-content: flex-end;
                  gap: 6px;
                }

                .session-action {
                  height: 28px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  gap: 6px;
                  padding: 0 9px;
                  border-color: var(--line-soft);
                  background: rgba(255, 255, 255, .035);
                  font-size: 12px;
                  font-weight: 760;
                  white-space: nowrap;
                }

                .empty {
                  border: 1px solid var(--line-soft);
                  border-radius: var(--radius);
                  background: rgba(255, 255, 255, .032);
                  padding: 14px;
                  color: var(--text-soft);
                  line-height: 1.45;
                }

                .filter-refresh {
                  width: 28px;
                  height: 28px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  border: 1px solid var(--line-soft);
                  border-radius: 6px;
                  color: var(--text-soft);
                  background: rgba(255, 255, 255, .035);
                  cursor: pointer;
                  transition: background .15s ease, border-color .15s ease, color .15s ease, transform .12s ease;
                }

                .filter-refresh:hover {
                  color: var(--text);
                  background: rgba(255, 255, 255, .07);
                  border-color: var(--line);
                }

                .filter-refresh:active {
                  transform: translateY(1px);
                }

                .filter-refresh:disabled {
                  opacity: 1;
                }

                .filter-refresh.is-refreshing {
                  color: var(--green);
                  background: var(--green-soft);
                  border-color: rgba(104, 217, 130, .46);
                  cursor: wait;
                }

                .filter-refresh svg {
                  width: 15px;
                  height: 15px;
                  display: block;
                }

                .filter-refresh.is-refreshing svg {
                  animation: agentdock-refresh-spin .72s linear infinite;
                }

                @keyframes agentdock-refresh-spin {
                  to { transform: rotate(360deg); }
                }

                .toast {
                  position: fixed;
                  right: 10px;
                  bottom: 52px;
                  max-width: calc(100% - 20px);
                  padding: 9px 10px;
                  border: 1px solid rgba(240, 107, 115, .36);
                  border-radius: 8px;
                  background: #2a1e20;
                  color: var(--text);
                  box-shadow: 0 18px 54px rgba(0, 0, 0, .42);
                  opacity: 0;
                  transform: translateY(8px);
                  pointer-events: none;
                  transition: opacity .18s ease, transform .18s ease;
                  font-size: 12px;
                }

                .toast.show {
                  opacity: 1;
                  transform: translateY(0);
                }
              </style>
            </head>
            <body>
              <main id="agentdock-root" class="agentdock"></main>
              <div id="agentdock-tooltip" class="agentdock-tooltip" role="tooltip"></div>
              <div id="agentdock-toast" class="toast"></div>
              <script>
                window.AgentDock = (function () {
                  var state = $stateJson;
                  var query = "";
                  var selectedProvider = "all";
                  var searchFocused = true;
                  var composingSearch = false;
                  var refreshing = false;
                  var refreshStartedAt = 0;
                  var refreshFinishTimer = null;
                  var sessionPreviewTimer = null;
                  var hoveredSessionId = null;
                  var requestedPreviewId = null;
                  var hoveredUsageProviderId = null;
                  var root = document.getElementById("agentdock-root");
                  var tooltip = document.getElementById("agentdock-tooltip");
                  var toast = document.getElementById("agentdock-toast");

                  function escapeHtml(value) {
                    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
                      return {"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[ch];
                    });
                  }

                  function attr(value) {
                    return escapeHtml(value).replace(/`/g, "&#96;");
                  }

                  function providerLogo(providerId) {
                    if (providerId === "codex") {
                      return '<svg class="logo" viewBox="0 0 24 24" aria-hidden="true"><path d="M9.205 8.658v-2.26c0-.19.072-.333.238-.428l4.543-2.616c.619-.357 1.356-.523 2.117-.523 2.854 0 4.662 2.212 4.662 4.566 0 .167 0 .357-.024.547l-4.71-2.759a.797.797 0 00-.856 0l-5.97 3.473zm10.609 8.8V12.06c0-.333-.143-.57-.429-.737l-5.97-3.473 1.95-1.118a.433.433 0 01.476 0l4.543 2.617c1.309.76 2.189 2.378 2.189 3.948 0 1.808-1.07 3.473-2.76 4.163zM7.802 12.703l-1.95-1.142c-.167-.095-.239-.238-.239-.428V5.899c0-2.545 1.95-4.472 4.591-4.472 1 0 1.927.333 2.712.928L8.23 5.067c-.285.166-.428.404-.428.737v6.898zM12 15.128l-2.795-1.57v-3.33L12 8.658l2.795 1.57v3.33L12 15.128zm1.796 7.23c-1 0-1.927-.332-2.712-.927l4.686-2.712c.285-.166.428-.404.428-.737v-6.898l1.974 1.142c.167.095.238.238.238.428v5.233c0 2.545-1.974 4.472-4.614 4.472zm-5.637-5.303l-4.544-2.617c-1.308-.761-2.188-2.378-2.188-3.948A4.482 4.482 0 014.21 6.327v5.423c0 .333.143.571.428.738l5.947 3.449-1.95 1.118a.432.432 0 01-.476 0zm-.262 3.9c-2.688 0-4.662-2.021-4.662-4.519 0-.19.024-.38.047-.57l4.686 2.71c.286.167.571.167.856 0l5.97-3.448v2.26c0 .19-.07.333-.237.428l-4.543 2.616c-.619.357-1.356.523-2.117.523zm5.899 2.83a5.947 5.947 0 005.827-4.756C22.287 18.339 24 15.84 24 13.296c0-1.665-.713-3.282-1.998-4.448.119-.5.19-.999.19-1.498 0-3.401-2.759-5.947-5.946-5.947-.642 0-1.26.095-1.88.31A5.962 5.962 0 0010.205 0a5.947 5.947 0 00-5.827 4.757C1.713 5.447 0 7.945 0 10.49c0 1.666.713 3.283 1.998 4.448-.119.5-.19 1-.19 1.499 0 3.401 2.759 5.946 5.946 5.946.642 0 1.26-.095 1.88-.309a5.96 5.96 0 004.162 1.713z" fill="#A8B2A3"></path></svg>';
                    }
                    if (providerId === "claude-code") {
                      return '<svg class="logo" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.709 15.955l4.72-2.647.08-.23-.08-.128H9.2l-.79-.048-2.698-.073-2.339-.097-2.266-.122-.571-.121L0 11.784l.055-.352.48-.321.686.06 1.52.103 2.278.158 1.652.097 2.449.255h.389l.055-.157-.134-.098-.103-.097-2.358-1.596-2.552-1.688-1.336-.972-.724-.491-.364-.462-.158-1.008.656-.722.881.06.225.061.893.686 1.908 1.476 2.491 1.833.365.304.145-.103.019-.073-.164-.274-1.355-2.446-1.446-2.49-.644-1.032-.17-.619a2.97 2.97 0 01-.104-.729L6.283.134 6.696 0l.996.134.42.364.62 1.414 1.002 2.229 1.555 3.03.456.898.243.832.091.255h.158V9.01l.128-1.706.237-2.095.23-2.695.08-.76.376-.91.747-.492.584.28.48.685-.067.444-.286 1.851-.559 2.903-.364 1.942h.212l.243-.242.985-1.306 1.652-2.064.73-.82.85-.904.547-.431h1.033l.76 1.129-.34 1.166-1.064 1.347-.881 1.142-1.264 1.7-.79 1.36.073.11.188-.02 2.856-.606 1.543-.28 1.841-.315.833.388.091.395-.328.807-1.969.486-2.309.462-3.439.813-.042.03.049.061 1.549.146.662.036h1.622l3.02.225.79.522.474.638-.079.485-1.215.62-1.64-.389-3.829-.91-1.312-.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33.127.578-.322.455-.34-.049-2.205-1.657-.851-.747-1.926-1.62h-.128v.17l.444.649 2.345 3.521.122 1.08-.17.353-.608.213-.668-.122-1.374-1.925-1.415-2.167-1.143-1.943-.14.08-.674 7.254-.316.37-.729.28-.607-.461-.322-.747.322-1.476.389-1.924.315-1.53.286-1.9.17-.632-.012-.042-.14.018-1.434 1.967-2.18 2.945-1.726 1.845-.414.164-.717-.37.067-.662.401-.589 2.388-3.036 1.44-1.882.93-1.086-.006-.158h-.055L4.132 18.56l-1.13.146-.487-.456.061-.746.231-.243 1.908-1.312-.006.006z" fill="#D97757"></path></svg>';
                    }
                    if (providerId === "gemini") {
                      return '<svg class="logo" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 1.5c1.18 5.25 5.25 9.32 10.5 10.5-5.25 1.18-9.32 5.25-10.5 10.5C10.82 17.25 6.75 13.18 1.5 12 6.75 10.82 10.82 6.75 12 1.5z" fill="#6EA8FE"></path></svg>';
                    }
                    return '<span class="logo" aria-hidden="true"></span>';
                  }

                  function renderTerminalIndicator(item) {
                    var active = item.terminalOpen ? " active" : "";
                    var label = item.terminalOpen ? "Terminal open" : "Terminal closed";
                    return '<span class="terminal-indicator' + active + '" title="' + label + '" aria-label="' + label + '" role="img"></span>';
                  }

                  function normalizedDailyTokens(item) {
                    var values = Array.isArray(item.dailyTokens) ? item.dailyTokens.slice(-7) : [];
                    values = values.map(function (value) {
                      var numeric = Number(value);
                      return Number.isFinite(numeric) ? Math.max(0, numeric) : 0;
                    });
                    while (values.length < 7) values.unshift(0);
                    return values;
                  }

                  function normalizedDailyResponseMillis(item) {
                    var values = Array.isArray(item.dailyAverageResponseMillis)
                      ? item.dailyAverageResponseMillis.slice(-7)
                      : [];
                    values = values.map(function (value) {
                      if (value === null || value === undefined) return null;
                      var numeric = Number(value);
                      return Number.isFinite(numeric) && numeric >= 0 ? numeric : null;
                    });
                    while (values.length < 7) values.unshift(null);
                    return values;
                  }

                  function formatTokenCount(value) {
                    var numeric = Math.max(0, Number(value) || 0);
                    function compact(divisor, suffix) {
                      var scaled = numeric / divisor;
                      var digits = scaled >= 100 ? 0 : 1;
                      return scaled.toFixed(digits).replace(/\.0$/, "") + suffix;
                    }
                    if (numeric >= 1000000000) return compact(1000000000, "B");
                    if (numeric >= 1000000) return compact(1000000, "M");
                    if (numeric >= 1000) return compact(1000, "K");
                    return String(Math.round(numeric));
                  }

                  function formatResponseTime(value) {
                    var millis = Math.max(0, Number(value) || 0);
                    var seconds = millis / 1000;
                    return seconds.toFixed(1).replace(/\.0$/, "") + "S";
                  }

                  function metricTrendGradientId(sessionId, metricKey) {
                    var safeId = String(sessionId || "session").replace(/[^a-zA-Z0-9_-]/g, "-");
                    return "agentdock-" + metricKey + "-trend-fill-" + safeId;
                  }

                  function tokenTrendGradientId(sessionId) {
                    return metricTrendGradientId(sessionId, "token");
                  }

                  function tokenTrendCurveSegments(points) {
                    var segments = "";
                    for (var index = 1; index < points.length; index += 1) {
                      var previous = points[index - 1];
                      var current = points[index];
                      var midpointX = (previous.x + current.x) / 2;
                      segments += " C " + midpointX.toFixed(2) + " " + previous.y.toFixed(2) +
                        " " + midpointX.toFixed(2) + " " + current.y.toFixed(2) +
                        " " + current.x.toFixed(2) + " " + current.y.toFixed(2);
                    }
                    return segments;
                  }

                  function renderMetricTrend(dailyValues, available, gradientId, availableLabel, unavailableLabel, formatter) {
                    var values = dailyValues;
                    var width = 240;
                    var height = 48;
                    var horizontalPadding = 3.25;
                    var topPadding = 8;
                    var bottomPadding = 5;
                    var indexedValues = values.map(function (value, index) {
                      var hasData = value !== null && value !== undefined && Number.isFinite(Number(value));
                      return {index: index, value: hasData ? Number(value) : 0, hasData: hasData};
                    });
                    var valuesWithData = indexedValues.filter(function (entry) { return entry.hasData; });
                    var maxValue = valuesWithData.length
                      ? Math.max.apply(null, valuesWithData.map(function (entry) { return entry.value; }))
                      : 0;
                    var points = available && valuesWithData.length ? indexedValues.map(function (entry) {
                      var x = horizontalPadding + entry.index * (width - horizontalPadding * 2) / (values.length - 1);
                      var y = maxValue > 0
                        ? height - bottomPadding - entry.value * (height - topPadding - bottomPadding) / maxValue
                        : height / 2;
                      return {x: x, y: y, value: entry.value, hasData: entry.hasData};
                    }) : [
                      {x: horizontalPadding, y: height / 2, value: 0, hasData: false},
                      {x: width - horizontalPadding, y: height / 2, value: 0, hasData: false}
                    ];
                    var curveSegments = tokenTrendCurveSegments(points);
                    var linePath = "M " + points[0].x.toFixed(2) + " " + points[0].y.toFixed(2) + curveSegments;
                    var baselineY = height - bottomPadding;
                    var areaPath = "M " + points[0].x.toFixed(2) + " " + baselineY.toFixed(2) +
                      " L " + points[0].x.toFixed(2) + " " + points[0].y.toFixed(2) + curveSegments +
                      " L " + points[points.length - 1].x.toFixed(2) + " " + baselineY.toFixed(2) + " Z";
                    var markers = available ? points.map(function (point) {
                      var left = point.x * 100 / width;
                      var top = point.y * 100 / height;
                      var missing = point.hasData ? "" : " missing";
                      return '<span class="token-trend-marker' + missing + '" aria-hidden="true" style="left:' + left.toFixed(2) + '%;top:' + top.toFixed(2) + '%"></span>';
                    }).join("") : "";
                    var label = available
                      ? availableLabel + dailyValues.map(function (value) {
                          return value === null || value === undefined ? "No data" : formatter(value);
                        }).join(", ")
                      : unavailableLabel;
                    var area = available && maxValue > 0 && points.length > 1
                      ? '<defs><linearGradient id="' + attr(gradientId) + '" gradientUnits="userSpaceOnUse" x1="0" y1="' + topPadding + '" x2="0" y2="' + baselineY + '">' +
                          '<stop offset="0%" stop-color="#4BDE80" stop-opacity="0.32"></stop>' +
                          '<stop offset="100%" stop-color="#4BDE80" stop-opacity="0"></stop>' +
                        '</linearGradient></defs>' +
                        '<path class="token-trend-area" d="' + areaPath + '" fill="url(#' + attr(gradientId) + ')"></path>'
                      : "";
                    return '<svg viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none" role="img" data-point-count="' + points.length + '" aria-label="' + attr(label) + '">' +
                      '<title>' + escapeHtml(label) + '</title>' +
                      area +
                      '<path class="token-trend-line" d="' + linePath + '" data-segment-count="' + (points.length - 1) + '"></path>' +
                    '</svg>' + markers;
                  }

                  function renderTokenTrend(dailyValues, available, sessionId) {
                    return renderMetricTrend(
                      dailyValues,
                      available,
                      tokenTrendGradientId(sessionId),
                      "7-day token usage, oldest to today: ",
                      "Token usage unavailable",
                      formatTokenCount
                    );
                  }

                  function renderResponseTrend(dailyValues, available, sessionId) {
                    return renderMetricTrend(
                      dailyValues,
                      available,
                      metricTrendGradientId(sessionId, "response"),
                      "7-day average response time, oldest to today: ",
                      "Average response time unavailable",
                      formatResponseTime
                    );
                  }

                  function renderTokenUsage(item) {
                    var available = item.totalTokens !== null && item.totalTokens !== undefined && Number.isFinite(Number(item.totalTokens));
                    var values = normalizedDailyTokens(item);
                    var total = available ? Math.max(0, Number(item.totalTokens)) : 0;
                    var totalLabel = available ? formatTokenCount(total) : "—";
                    var totalTitle = available
                      ? "Historical total token usage: " + Math.round(total).toLocaleString("en-US")
                      : "Historical token usage unavailable";
                    return '<div class="session-token-usage" aria-label="Token usage">' +
                      '<span class="session-token-label">Token Usage</span>' +
                      '<span class="session-token-chart' + (available ? "" : " unavailable") + '">' + renderTokenTrend(values, available, item.id) + '</span>' +
                      '<span class="session-token-total" title="' + attr(totalTitle) + '">' + escapeHtml(totalLabel) + '</span>' +
                    '</div>';
                  }

                  function renderAverageResponseTime(item) {
                    var values = normalizedDailyResponseMillis(item);
                    var available = values.some(function (value) { return value !== null; });
                    var today = values[values.length - 1];
                    var todayAvailable = today !== null;
                    var todayLabel = todayAvailable ? formatResponseTime(today) : "—";
                    var todayTitle = todayAvailable
                      ? "Today's average response time: " + formatResponseTime(today)
                      : "No response recorded today";
                    return '<div class="session-response-time" aria-label="Average response time">' +
                      '<span class="session-response-label">Avg. Time</span>' +
                      '<span class="session-response-chart' + (available ? "" : " unavailable") + '">' + renderResponseTrend(values, available, item.id) + '</span>' +
                      '<span class="session-response-today" title="' + attr(todayTitle) + '">' + escapeHtml(todayLabel) + '</span>' +
                    '</div>';
                  }

                  function renderSessionMetrics(item) {
                    return '<div class="session-metrics">' +
                      renderTokenUsage(item) +
                      renderAverageResponseTime(item) +
                    '</div>';
                  }

                  function providerOptions() {
                    var seen = {};
                    var options = [];
                    function addProvider(id, name) {
                      if (!id || seen[id]) return;
                      seen[id] = true;
                      options.push({id: id, name: name || id});
                    }
                    (state.providers || []).forEach(function (provider) {
                      addProvider(provider.id, provider.name);
                    });
                    (state.sessions || []).forEach(function (session) {
                      addProvider(session.providerId, session.providerName);
                    });
                    return options;
                  }

                  function matchesProvider(item) {
                    if (selectedProvider === "all") return true;
                    return item.providerId === selectedProvider;
                  }

                  function filteredSessions() {
                    var q = query.trim().toLowerCase();
                    return state.sessions.filter(function (item) {
                      var text = [item.title, item.summary, item.providerName, item.statusLabel].join(" ").toLowerCase();
                      return matchesProvider(item) && (!q || text.indexOf(q) >= 0);
                    });
                  }

                  function renderSearch() {
                    return '<section class="search-row">' +
                      '<input id="agentdock-search" class="search" placeholder="Search sessions" value="' + attr(query) + '">' +
                    '</section>';
                  }

                  function renderFilters() {
                    var allActive = selectedProvider === "all" ? " active" : "";
                    var countLabel = String(state.count || 0);
                    var countHint = "This project has " + countLabel + " " + (state.count === 1 ? "session." : "sessions.");
                    var refreshClass = refreshing ? " is-refreshing" : "";
                    var refreshState = refreshing ? ' disabled aria-busy="true" title="正在刷新会话"' : ' aria-busy="false" title="刷新会话"';
                    var providerButtons = providerOptions().map(function (provider) {
                      var active = selectedProvider === provider.id ? " active" : "";
                      return '<button class="provider-filter' + active + '" data-provider="' + attr(provider.id) + '" data-provider-usage="' + attr(provider.id) + '" data-provider-name="' + attr(provider.name) + '" aria-label="' + attr(provider.name) + '">' +
                        providerLogo(provider.id) +
                      '</button>';
                    }).join("");
                    return '<nav class="provider-filters" aria-label="AI provider filters">' +
                      '<div class="provider-filter-set">' +
                        '<button class="provider-filter all' + allActive + '" data-provider="all" data-hint="' + attr(countHint) + '" aria-describedby="agentdock-tooltip" aria-label="All AI providers, ' + attr(countHint) + '">All<span class="provider-count">' + escapeHtml(countLabel) + '</span></button>' +
                        providerButtons +
                      '</div>' +
                      '<button class="filter-refresh' + refreshClass + '" data-action="refresh" aria-label="刷新会话"' + refreshState + '>' +
                        '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 12a8 8 0 0 1-13.66 5.66M4 12A8 8 0 0 1 17.66 6.34M18 3v4h-4M6 21v-4h4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
                      '</button>' +
                    '</nav>';
                  }

                  function renderCard(item) {
                    return '<article class="session-card" data-session-id="' + attr(item.id) + '">' +
                      '<div class="session-top">' +
                        providerLogo(item.providerId) +
                        '<div class="session-copy">' +
                          '<div class="session-name" data-session-preview-id="' + attr(item.id) + '" aria-label="' + attr(item.title) + '">' + escapeHtml(item.title) + '</div>' +
                        '</div>' +
                        renderTerminalIndicator(item) +
                      '</div>' +
                      renderSessionMetrics(item) +
                      '<div class="session-footer">' +
                        '<div class="session-time" title="' + attr(item.updatedLabel) + '">' + escapeHtml(item.updatedLabel) + '</div>' +
                        '<div class="session-actions">' +
                          '<button class="session-action" data-action="open" data-id="' + attr(item.id) + '">Open</button>' +
                          '<button class="session-action yolo" data-action="open-yolo" data-id="' + attr(item.id) + '" title="Open in YOLO mode (bypasses permission checks)" aria-label="Open session in YOLO mode">YOLO</button>' +
                          '<button class="session-action" data-action="pin" data-id="' + attr(item.id) + '">' + (item.pinned ? "Unpin" : "Pin") + '</button>' +
                        '</div>' +
                      '</div>' +
                    '</article>';
                  }

                  function renderList() {
                    var sessions = filteredSessions();
                    if (!sessions.length) {
                      return '<section class="sessions-list"><div class="empty">No matching sessions</div></section>';
                    }
                    return '<section class="sessions-list">' + sessions.map(renderCard).join("") + '</section>';
                  }

                  function beginReloadFeedback() {
                    if (refreshFinishTimer) {
                      window.clearTimeout(refreshFinishTimer);
                      refreshFinishTimer = null;
                    }
                    refreshing = true;
                    refreshStartedAt = Date.now();
                    render({focusSearch: false});
                  }

                  function finishReloadFeedback() {
                    if (!refreshing) return false;
                    var remaining = Math.max(0, 280 - (Date.now() - refreshStartedAt));
                    if (remaining > 0) {
                      refreshFinishTimer = window.setTimeout(function () {
                        refreshing = false;
                        refreshFinishTimer = null;
                        if (!composingSearch) render();
                      }, remaining);
                      return true;
                    }
                    refreshing = false;
                    return false;
                  }

                  function bind(forceSearchFocus) {
                    var input = document.getElementById("agentdock-search");
                    if (input) {
                      if (forceSearchFocus || searchFocused) {
                        input.focus();
                        input.setSelectionRange(input.value.length, input.value.length);
                      }
                      input.addEventListener("focus", function () {
                        searchFocused = true;
                      });
                      input.addEventListener("blur", function () {
                        searchFocused = false;
                      });
                      input.addEventListener("compositionstart", function () {
                        composingSearch = true;
                      });
                      input.addEventListener("compositionend", function () {
                        composingSearch = false;
                        query = input.value;
                        searchFocused = true;
                        render({focusSearch: true});
                      });
                      input.addEventListener("input", function (event) {
                        query = input.value;
                        if (composingSearch || event.isComposing) return;
                        searchFocused = true;
                        render({focusSearch: true});
                      });
                    }
                    root.querySelectorAll("[data-provider]").forEach(function (button) {
                      button.addEventListener("click", function () {
                        hideSessionHint();
                        hideProviderUsage();
                        searchFocused = false;
                        selectedProvider = button.getAttribute("data-provider") || "all";
                        render({focusSearch: false});
                      });
                    });
                    var allButton = root.querySelector(".provider-filter.all");
                    if (allButton) {
                      allButton.addEventListener("mouseenter", function () { showSessionHint(allButton); });
                      allButton.addEventListener("mouseleave", hideSessionHint);
                      allButton.addEventListener("focus", function () { showSessionHint(allButton); });
                      allButton.addEventListener("blur", hideSessionHint);
                    }
                    root.querySelectorAll("[data-provider-usage]").forEach(function (button) {
                      button.addEventListener("mouseenter", function () { showProviderUsage(button, true); });
                      button.addEventListener("mouseleave", hideProviderUsage);
                      button.addEventListener("focus", function () { showProviderUsage(button, false); });
                      button.addEventListener("blur", hideProviderUsage);
                    });
                    root.querySelectorAll("[data-action]").forEach(function (button) {
                      button.addEventListener("click", function () {
                        hideSessionPreview(true);
                        hideProviderUsage();
                        var action = button.getAttribute("data-action");
                        var id = button.getAttribute("data-id");
                        if (action === "refresh") beginReloadFeedback();
                        send(action, {id: id});
                      });
                    });
                    root.querySelectorAll(".session-name[data-session-preview-id]").forEach(function (title) {
                      title.addEventListener("mouseenter", function () {
                        scheduleSessionPreview(title);
                      });
                      title.addEventListener("mouseleave", function () {
                        hideSessionPreview(false);
                      });
                    });
                    var sessionsList = root.querySelector(".sessions-list");
                    if (sessionsList) {
                      sessionsList.addEventListener("scroll", function () {
                        hideSessionPreview(true);
                        hideProviderUsage();
                      }, {passive: true});
                    }
                  }

                  function render(options) {
                    var forceSearchFocus = Boolean(options && options.focusSearch);
                    var keepSearchFocus = forceSearchFocus || searchFocused;
                    hideSessionHint();
                    hideProviderUsage();
                    hideSessionPreview(true);
                    root.innerHTML = renderSearch() + renderFilters() + renderList();
                    if (keepSearchFocus) searchFocused = true;
                    bind(keepSearchFocus);
                  }

                  function showSessionHint(button) {
                    var message = button.getAttribute("data-hint") || "";
                    if (!message) return;
                    tooltip.textContent = message;
                    tooltip.classList.add("show");
                    var rect = button.getBoundingClientRect();
                    var hintRect = tooltip.getBoundingClientRect();
                    var left = rect.left + rect.width / 2;
                    var minLeft = 8 + hintRect.width / 2;
                    var maxLeft = window.innerWidth - 8 - hintRect.width / 2;
                    tooltip.style.left = Math.max(minLeft, Math.min(maxLeft, left)) + "px";
                    tooltip.style.top = Math.max(8, rect.top) + "px";
                  }

                  function hideSessionHint() {
                    tooltip.classList.remove("show");
                  }

                  function showProviderUsage(button, usePointer) {
                    var providerId = button.getAttribute("data-provider-usage") || "";
                    if (!providerId) return;
                    hideSessionHint();
                    hoveredUsageProviderId = providerId;
                    var rect = button.getBoundingClientRect();
                    send("provider-usage-show", {
                      providerId: providerId,
                      left: Math.round(rect.left),
                      top: Math.round(rect.top),
                      width: Math.round(rect.width),
                      height: Math.round(rect.height),
                      usePointer: Boolean(usePointer)
                    });
                  }

                  function hideProviderUsage() {
                    if (!hoveredUsageProviderId) return;
                    hoveredUsageProviderId = null;
                    send("provider-usage-hide", {});
                  }

                  function scheduleSessionPreview(title) {
                    if (sessionPreviewTimer) {
                      window.clearTimeout(sessionPreviewTimer);
                    }
                    var card = title.closest(".session-card[data-session-id]");
                    var sessionId = title.getAttribute("data-session-preview-id") || "";
                    if (!card) return;
                    if (!sessionId) return;
                    hoveredSessionId = sessionId;
                    sessionPreviewTimer = window.setTimeout(function () {
                      sessionPreviewTimer = null;
                      if (hoveredSessionId !== sessionId || !title.isConnected || !card.isConnected) return;
                      var rect = card.getBoundingClientRect();
                      requestedPreviewId = sessionId;
                      send("preview-show", {
                        id: sessionId,
                        left: Math.round(rect.left),
                        top: Math.round(rect.top),
                        width: Math.round(rect.width),
                        height: Math.round(rect.height)
                      });
                    }, 120);
                  }

                  function hideSessionPreview(immediate) {
                    if (sessionPreviewTimer) {
                      window.clearTimeout(sessionPreviewTimer);
                      sessionPreviewTimer = null;
                    }
                    hoveredSessionId = null;
                    if (!requestedPreviewId) return;
                    requestedPreviewId = null;
                    send("preview-hide", {immediate: Boolean(immediate)});
                  }

                  function send(action, payload) {
                    var data = payload || {};
                    data.action = action;
                    var message = JSON.stringify(data);
                    $bridgeScript
                  }

                  function receive(response) {
                    try {
                      var payload = JSON.parse(response || "{}");
                      if (payload.state) state = payload.state;
                      if (payload.query != null) query = payload.query;
                      if (payload.error) showError(payload.error);
                      if (payload.preserveView) {
                        return;
                      }
                      if (payload.refreshPending) {
                        return;
                      }
                      var waitingForReloadFeedback = finishReloadFeedback();
                      if (composingSearch) {
                        return;
                      }
                      if (waitingForReloadFeedback) {
                        return;
                      }
                      render();
                    } catch (error) {
                      showError(String(error));
                    }
                  }

                  function showError(message) {
                    if (refreshFinishTimer) {
                      window.clearTimeout(refreshFinishTimer);
                      refreshFinishTimer = null;
                    }
                    if (refreshing) {
                      refreshing = false;
                      render();
                    }
                    toast.textContent = message || "AgentDock error";
                    toast.classList.add("show");
                    window.setTimeout(function () { toast.classList.remove("show"); }, 2400);
                  }

                  return {
                    render: render,
                    receive: receive,
                    showError: showError
                  };
                })();
                window.AgentDock.render();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun actionResponseJson(response: ActionResponse): String = scriptJson(response)

    fun refreshPendingResponseJson(): String = scriptJson(ActionResponse(refreshPending = true))

    fun errorResponseJson(error: String): String = scriptJson(ActionResponse(error = error))

    private fun scriptJson(value: Any): String {
        return gson.toJson(value)
            .replace("</", "<\\/")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
    }
}
