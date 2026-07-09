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
        val refreshPending: Boolean = false
    )

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
                  --blue: #73a7ff;
                  --blue-soft: rgba(115, 167, 255, .14);
                  --orange: #d97757;
                  --yellow: #e8b75d;
                  --red: #f06b73;
                  --radius: 8px;
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
                  border-left: 1px solid var(--line-soft);
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
                  padding: 8px 10px;
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

                .provider-filters {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) auto;
                  align-items: center;
                  gap: 8px;
                  padding: 7px 10px;
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
                  padding: 9px 10px 16px;
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

                .session-summary {
                  margin-top: 7px;
                  color: var(--text-soft);
                  font-size: 13px;
                  line-height: 1.4;
                  display: -webkit-box;
                  -webkit-line-clamp: 2;
                  -webkit-box-orient: vertical;
                  overflow: hidden;
                }

                .session-footer {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) auto;
                  align-items: center;
                  gap: 8px;
                  margin-top: 8px;
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
                    return '<span class="logo" aria-hidden="true"></span>';
                  }

                  function renderTerminalIndicator(item) {
                    var active = item.terminalOpen ? " active" : "";
                    var label = item.terminalOpen ? "Terminal open" : "Terminal closed";
                    return '<span class="terminal-indicator' + active + '" title="' + label + '" aria-label="' + label + '" role="img"></span>';
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
                      return '<button class="provider-filter' + active + '" data-provider="' + attr(provider.id) + '" title="' + attr(provider.name) + '" aria-label="' + attr(provider.name) + '">' +
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
                    return '<article class="session-card">' +
                      '<div class="session-top">' +
                        providerLogo(item.providerId) +
                        '<div class="session-copy">' +
                          '<div class="session-name" title="' + attr(item.title) + '">' + escapeHtml(item.title) + '</div>' +
                        '</div>' +
                        renderTerminalIndicator(item) +
                      '</div>' +
                      '<div class="session-summary">' + escapeHtml(item.summary || item.title || "No summary captured yet") + '</div>' +
                      '<div class="session-footer">' +
                        '<div class="session-time" title="' + attr(item.updatedLabel) + '">' + escapeHtml(item.updatedLabel) + '</div>' +
                        '<div class="session-actions">' +
                          '<button class="session-action" data-action="open" data-id="' + attr(item.id) + '">Open</button>' +
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
                    root.querySelectorAll("[data-action]").forEach(function (button) {
                      button.addEventListener("click", function () {
                        var action = button.getAttribute("data-action");
                        var id = button.getAttribute("data-id");
                        if (action === "refresh") beginReloadFeedback();
                        send(action, {id: id});
                      });
                    });
                  }

                  function render(options) {
                    var forceSearchFocus = Boolean(options && options.focusSearch);
                    var keepSearchFocus = forceSearchFocus || searchFocused;
                    hideSessionHint();
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

                  return {render: render, receive: receive, showError: showError};
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
