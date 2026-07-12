import type { ProviderId, ProviderUsageView, SessionCardView, SessionPreview } from "../core/model.js";
import type {
  DashboardProviderView,
  DashboardViewState,
  ExtensionToWebviewMessage,
  WebviewToExtensionMessage
} from "./protocol.js";
import { dashboardStyles } from "./styles.js";

interface VsCodeApi {
  postMessage(message: WebviewToExtensionMessage): void;
}

declare function acquireVsCodeApi(): VsCodeApi;

const vscode = acquireVsCodeApi();
const app = requiredElement("#app");
const overlayRoot = requiredElement("#overlay-root");
const previews = new Map<string, SessionPreview>();
const providerUsage = new Map<ProviderId, ProviderUsageView>();
let state: DashboardViewState = {
  type: "dashboard-state",
  projectPath: null,
  loading: true,
  refreshing: false,
  providers: [],
  sessions: []
};
let query = "";
let providerFilter: "all" | ProviderId = "all";
let activePreviewId: string | undefined;
let activeUsageId: ProviderId | undefined;
let previewAnchor: HTMLElement | undefined;
let usageAnchor: HTMLElement | undefined;
let hidePreviewTimer: number | undefined;
let hideUsageTimer: number | undefined;
let toastTimer: number | undefined;
let keyboardNavigation = false;

const style = document.createElement("style");
style.textContent = dashboardStyles;
document.head.append(style);

window.addEventListener("keydown", (event) => {
  if (event.key === "Tab") keyboardNavigation = true;
}, true);
window.addEventListener("pointerdown", () => {
  keyboardNavigation = false;
}, true);
window.addEventListener("pointermove", (event) => {
  if (!activeUsageId) return;
  const target = event.target;
  const withinUsageSurface = target instanceof Element
    && (target.closest(".usage-popup") !== null || target.closest(".filter-button[data-provider-id]") !== null);
  if (withinUsageSurface) {
    if (hideUsageTimer !== undefined) window.clearTimeout(hideUsageTimer);
  } else {
    scheduleHideUsage();
  }
}, true);
window.addEventListener("blur", () => {
  if (activeUsageId) scheduleHideUsage();
});
document.addEventListener("pointerleave", () => {
  if (activeUsageId) scheduleHideUsage();
});

window.addEventListener("message", (event: MessageEvent<ExtensionToWebviewMessage>) => {
  const message = event.data;
  if (message.type === "dashboard-state") {
    state = message;
    render();
  } else if (message.type === "session-preview") {
    previews.set(message.sessionId, message.preview);
    if (activePreviewId === message.sessionId) renderPreviewPopup();
  } else if (message.type === "provider-usage") {
    providerUsage.set(message.usage.providerId, message.usage);
    if (activeUsageId === message.usage.providerId) renderUsagePopup();
  } else if (message.type === "action-error") {
    showToast(message.message);
  }
});

window.addEventListener("resize", () => {
  if (activePreviewId) renderPreviewPopup();
  if (activeUsageId) renderUsagePopup();
});

function render(): void {
  const activeElement = document.activeElement;
  const focusedSearch = activeElement instanceof HTMLInputElement && activeElement.classList.contains("search-input");
  const cursor = focusedSearch ? activeElement.selectionStart : null;
  app.replaceChildren(toolbar());

  if (state.loading && !state.sessions.length) {
    app.append(skeleton());
  } else if (!state.projectPath) {
    app.append(emptyState("Open a folder", "AgentDock shows AI CLI sessions for the current workspace."));
  } else if (state.error && !state.sessions.length) {
    app.append(emptyState("Could not load sessions", state.error));
  } else {
    const sessions = filteredSessions();
    const list = element("section", "session-list");
    for (const session of sessions) list.append(sessionCard(session));
    app.append(sessions.length ? list : emptyState("No matching sessions", "Try another provider or search term."));
  }

  if (focusedSearch) {
    requestAnimationFrame(() => {
      const search = document.querySelector<HTMLInputElement>(".search-input");
      search?.focus();
      if (cursor !== null) search?.setSelectionRange(cursor, cursor);
    });
  }
  if (activePreviewId) renderPreviewPopup();
  if (activeUsageId) renderUsagePopup();
}

function toolbar(): HTMLElement {
  const container = element("header", "toolbar");
  const searchWrap = element("div", "search-wrap");
  searchWrap.append(element("span", "search-icon"));
  const search = element("input", "search-input");
  search.type = "search";
  search.placeholder = "Search sessions";
  search.value = query;
  search.setAttribute("aria-label", "Search sessions");
  search.addEventListener("input", () => {
    query = search.value;
    render();
  });
  searchWrap.append(search);

  const row = element("div", "filter-row");
  const filters = element("nav", "filters");
  filters.setAttribute("aria-label", "AI provider filters");
  filters.append(filterButton("all", "All", state.sessions.length));
  for (const provider of state.providers.filter((candidate) => candidate.enabled)) {
    const count = state.sessions.filter((session) => session.providerId === provider.id).length;
    const button = providerFilterButton(provider, count);
    filters.append(button);
  }
  const refresh = button("↻", () => {
    previews.clear();
    providerUsage.clear();
    vscode.postMessage({ type: "refresh" });
  }, "icon-button");
  refresh.classList.toggle("refreshing", state.refreshing);
  refresh.title = "Refresh sessions";
  refresh.setAttribute("aria-label", "Refresh sessions");
  row.append(filters, refresh);
  container.append(searchWrap, row);
  return container;
}

function filterButton(id: "all", label: string, count: number): HTMLButtonElement {
  const control = button("", () => {
    providerFilter = id;
    render();
  }, `filter-button all${providerFilter === id ? " active" : ""}`);
  const text = document.createElement("span");
  text.textContent = label;
  const badge = element("span", "filter-count");
  badge.textContent = String(count);
  control.append(text, badge);
  control.setAttribute("aria-pressed", String(providerFilter === id));
  return control;
}

function providerFilterButton(provider: DashboardProviderView, count: number): HTMLButtonElement {
  const control = button("", () => selectProvider(provider.id), `filter-button${providerFilter === provider.id ? " active" : ""}`);
  control.dataset.providerId = provider.id;
  control.append(providerImage(provider, "provider-logo"));
  control.title = `${provider.displayName} (${count})`;
  control.setAttribute("aria-label", `${provider.displayName}, ${count} sessions`);
  control.setAttribute("aria-pressed", String(providerFilter === provider.id));
  control.addEventListener("pointerdown", (event) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    selectProvider(provider.id);
  });
  control.addEventListener("pointerenter", () => showUsage(provider.id, control));
  control.addEventListener("pointerleave", scheduleHideUsage);
  control.addEventListener("focus", () => showUsage(provider.id, control));
  control.addEventListener("blur", scheduleHideUsage);
  if (activeUsageId === provider.id) usageAnchor = control;
  return control;
}

function selectProvider(providerId: ProviderId): void {
  providerFilter = providerId;
  render();
  requestAnimationFrame(() => {
    document.querySelector<HTMLButtonElement>(`.filter-button[data-provider-id="${providerId}"]`)?.focus();
  });
}

function filteredSessions(): SessionCardView[] {
  const normalized = query.trim().toLocaleLowerCase();
  return state.sessions.filter((session) => {
    const providerMatches = providerFilter === "all" || session.providerId === providerFilter;
    const queryMatches = !normalized || `${session.name} ${session.summary} ${session.cwd} ${session.providerName}`.toLocaleLowerCase().includes(normalized);
    return providerMatches && queryMatches;
  });
}

function sessionCard(session: SessionCardView): HTMLElement {
  const card = element("article", "session-card");
  card.dataset.sessionId = session.id;
  const provider = providerFor(session.providerId);
  const header = element("header", "session-header");
  const providerState = element("span", `session-provider-state ${session.taskState}`);
  providerState.title = terminalTaskStateLabel(session.taskState);
  providerState.setAttribute("role", "img");
  providerState.setAttribute("aria-label", providerState.title);
  if (provider) {
    const image = providerImage(provider, "session-provider-logo");
    image.alt = "";
    providerState.append(image);
  }
  header.append(providerState);
  const title = element("div", "session-title");
  title.textContent = session.name;
  title.title = session.name;
  title.dataset.previewId = session.id;
  title.tabIndex = 0;
  title.addEventListener("pointerenter", () => showPreview(session.id, title));
  title.addEventListener("pointerleave", scheduleHidePreview);
  title.addEventListener("focus", () => showPreview(session.id, title));
  title.addEventListener("blur", scheduleHidePreview);
  if (activePreviewId === session.id) previewAnchor = title;
  const status = element("div", "session-state");
  const dot = element("span", `terminal-dot${session.terminalOpen ? " open" : ""}`);
  dot.title = session.terminalOpen ? "AgentDock terminal open" : "No active AgentDock terminal";
  dot.setAttribute("role", "img");
  dot.setAttribute("aria-label", dot.title);
  status.append(dot);
  header.append(title, status);

  const metrics = element("div", "metrics");
  metrics.append(
    metric("Token Usage", session.dailyTokens, session.totalTokens === null ? "—" : formatCompact(session.totalTokens), session.totalTokens !== null),
    metric(
      "Avg. Time",
      session.dailyAverageResponseMillis,
      formatSeconds(session.dailyAverageResponseMillis.at(-1) ?? null),
      session.dailyAverageResponseMillis.some((value) => value !== null)
    )
  );

  card.append(header, metrics);

  const footer = element("footer", "session-footer");
  const updated = element("time", "updated");
  updated.dateTime = new Date(session.updatedAt).toISOString();
  updated.textContent = session.updatedLabel;
  const open = button("Open", () => vscode.postMessage({ type: "open-session", sessionId: session.id, yolo: false }), "action-button");
  const yolo = button("YOLO", () => vscode.postMessage({ type: "open-session", sessionId: session.id, yolo: true }), "action-button yolo");
  yolo.title = "Open with provider-specific permission bypass flags";
  const pin = button(session.pinned ? "Unpin" : "Pin", () => vscode.postMessage({ type: "toggle-pin", sessionId: session.id }), "action-button");
  const actions = element("div", "session-actions");
  actions.append(open, yolo, pin);
  footer.append(updated, actions);
  card.append(footer);
  return card;
}

function metric(label: string, rawValues: readonly (number | null)[], value: string, available: boolean): HTMLElement {
  const container = element("div", "metric");
  const labelNode = element("span", "metric-label");
  labelNode.textContent = label;
  const graph = sparkline(rawValues, available, label);
  const valueNode = element("span", "metric-value");
  valueNode.textContent = value;
  container.append(labelNode, graph, valueNode);
  return container;
}

function sparkline(rawValues: readonly (number | null)[], available: boolean, label: string): HTMLElement {
  const width = 240;
  const height = 48;
  const horizontalPadding = 3.25;
  const topPadding = 8;
  const bottomPadding = 5;
  const normalizedValues = rawValues.slice(-7);
  const paddedValues = Array.from<(number | null)>({ length: Math.max(0, 7 - normalizedValues.length) }).fill(null).concat(normalizedValues);
  const entries = paddedValues.map((rawValue, index) => {
    const numericValue = rawValue === null || rawValue === undefined ? Number.NaN : Number(rawValue);
    const hasData = Number.isFinite(numericValue);
    return { index, value: hasData ? Math.max(0, numericValue) : 0, hasData };
  });
  const valuesWithData = entries.filter((entry) => entry.hasData);
  const maximum = valuesWithData.length ? Math.max(...valuesWithData.map((entry) => entry.value)) : 0;
  const points = available && valuesWithData.length
    ? entries.map((entry) => ({
        x: horizontalPadding + entry.index * ((width - horizontalPadding * 2) / Math.max(1, entries.length - 1)),
        y: maximum > 0
          ? height - bottomPadding - entry.value * ((height - topPadding - bottomPadding) / maximum)
          : height / 2,
        hasData: entry.hasData
      }))
    : [
        { x: horizontalPadding, y: height / 2, hasData: false },
        { x: width - horizontalPadding, y: height / 2, hasData: false }
      ];
  const pathData = metricTrendPath(points);
  const baselineY = height - bottomPadding;
  const wrapper = element("span", `sparkline${available ? "" : " unavailable"}`);
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("preserveAspectRatio", "none");
  svg.setAttribute("role", "img");
  svg.setAttribute("data-point-count", String(points.length));
  const ariaLabel = `${label}, oldest to today: ${entries.map((entry) => entry.hasData ? entry.value : "No data").join(", ")}`;
  svg.setAttribute("aria-label", ariaLabel);
  const title = document.createElementNS(svg.namespaceURI, "title");
  title.textContent = ariaLabel;
  svg.append(title);
  const gradientId = `gradient-${Math.random().toString(36).slice(2)}`;
  const defs = document.createElementNS(svg.namespaceURI, "defs");
  const gradient = document.createElementNS(svg.namespaceURI, "linearGradient");
  gradient.id = gradientId;
  gradient.setAttribute("gradientUnits", "userSpaceOnUse");
  gradient.setAttribute("x1", "0");
  gradient.setAttribute("y1", String(topPadding));
  gradient.setAttribute("x2", "0");
  gradient.setAttribute("y2", String(baselineY));
  const top = document.createElementNS(svg.namespaceURI, "stop");
  top.setAttribute("offset", "0%");
  top.setAttribute("stop-color", "#4bde80");
  top.setAttribute("stop-opacity", available && maximum > 0 ? ".32" : "0");
  const bottom = document.createElementNS(svg.namespaceURI, "stop");
  bottom.setAttribute("offset", "100%");
  bottom.setAttribute("stop-color", "#4bde80");
  bottom.setAttribute("stop-opacity", "0");
  gradient.append(top, bottom);
  defs.append(gradient);
  svg.append(defs);
  if (available && maximum > 0 && points.length > 1) {
    const area = document.createElementNS(svg.namespaceURI, "path");
    area.classList.add("sparkline-area");
    area.setAttribute("d", `M ${points[0]?.x.toFixed(2) ?? horizontalPadding} ${baselineY.toFixed(2)} L ${pathData.slice(2)} L ${points.at(-1)?.x.toFixed(2) ?? width - horizontalPadding} ${baselineY.toFixed(2)} Z`);
    area.setAttribute("fill", `url(#${gradientId})`);
    svg.append(area);
  }
  const line = document.createElementNS(svg.namespaceURI, "path");
  line.classList.add("sparkline-line");
  line.setAttribute("d", pathData);
  svg.append(line);
  wrapper.append(svg);
  if (available) points.forEach((point) => {
    const marker = element("span", `sparkline-marker${point.hasData ? "" : " missing"}`);
    marker.style.left = `${(point.x * 100 / width).toFixed(2)}%`;
    marker.style.top = `${(point.y * 100 / height).toFixed(2)}%`;
    marker.setAttribute("aria-hidden", "true");
    wrapper.append(marker);
  });
  return wrapper;
}

function metricTrendPath(points: readonly { x: number; y: number }[]): string {
  if (!points.length) return "";
  let path = `M ${points[0]?.x.toFixed(2) ?? "0"} ${points[0]?.y.toFixed(2) ?? "0"}`;
  for (let index = 1; index < points.length; index += 1) {
    const previous = points[index - 1];
    const current = points[index];
    if (!previous || !current) continue;
    const midpointX = (previous.x + current.x) / 2;
    path += ` C ${midpointX.toFixed(2)} ${previous.y.toFixed(2)} ${midpointX.toFixed(2)} ${current.y.toFixed(2)} ${current.x.toFixed(2)} ${current.y.toFixed(2)}`;
  }
  return path;
}

function showPreview(sessionId: string, anchor: HTMLElement): void {
  if (hidePreviewTimer !== undefined) window.clearTimeout(hidePreviewTimer);
  activePreviewId = sessionId;
  previewAnchor = anchor;
  renderPreviewPopup();
  if (!previews.has(sessionId)) vscode.postMessage({ type: "load-preview", sessionId });
}

function scheduleHidePreview(): void {
  if (hidePreviewTimer !== undefined) window.clearTimeout(hidePreviewTimer);
  hidePreviewTimer = window.setTimeout(() => {
    activePreviewId = undefined;
    previewAnchor = undefined;
    removePopup("preview-popup");
  }, 220);
}

function renderPreviewPopup(): void {
  removePopup("preview-popup");
  const session = state.sessions.find((candidate) => candidate.id === activePreviewId);
  if (!session || !previewAnchor) return;
  const popup = element("section", "popup preview-popup");
  const header = element("header", "preview-header");
  const title = element("div", "preview-title");
  title.textContent = session.name;
  const preview = previews.get(session.id);
  const totalMessages = preview ? preview.messages.length + preview.omittedMessageCount : 0;
  const meta = element("div", "preview-meta");
  meta.textContent = preview ? `${session.providerName}  |  ${totalMessages} ${totalMessages === 1 ? "message" : "messages"}` : `${session.providerName}  |  Loading...`;
  header.append(title, meta);
  const conversation = element("div", "conversation");
  if (!preview) {
    conversation.append(notice("Loading conversation..."));
  } else if (!preview.messages.length) {
    conversation.append(notice("No conversation content is available."));
  } else {
    preview.messages.forEach((message, index) => {
      conversation.append(messageRow(session.providerId, message.role, message.text));
      if (index === 0 && preview.omittedMessageCount > 0) {
        conversation.append(notice(`${preview.omittedMessageCount} earlier messages omitted`));
      }
    });
  }
  if (preview?.notice) conversation.append(notice(preview.notice));
  popup.append(header, conversation);
  popup.addEventListener("pointerenter", () => {
    if (hidePreviewTimer !== undefined) window.clearTimeout(hidePreviewTimer);
  });
  popup.addEventListener("pointerleave", scheduleHidePreview);
  overlayRoot.append(popup);
  positionPopup(popup, previewAnchor, "left");
}

function messageRow(providerId: ProviderId, role: "user" | "assistant", text: string): HTMLElement {
  const row = element("div", `message-row ${role}`);
  const bubble = element("div", "bubble");
  bubble.textContent = text;
  if (role === "assistant") {
    const provider = providerFor(providerId);
    const avatar = element("span", "avatar");
    if (provider) avatar.append(providerImage(provider, ""));
    row.append(avatar, bubble);
  } else {
    row.append(bubble, element("span", "avatar user-avatar"));
  }
  return row;
}

function showUsage(providerId: ProviderId, anchor: HTMLElement): void {
  if (hideUsageTimer !== undefined) window.clearTimeout(hideUsageTimer);
  activeUsageId = providerId;
  usageAnchor = anchor;
  renderUsagePopup();
  if (!providerUsage.has(providerId)) vscode.postMessage({ type: "load-provider-usage", providerId });
}

function scheduleHideUsage(): void {
  if (hideUsageTimer !== undefined) window.clearTimeout(hideUsageTimer);
  hideUsageTimer = window.setTimeout(() => {
    const popup = overlayRoot.querySelector<HTMLElement>(".usage-popup");
    if (usageAnchor?.matches(":hover") || popup?.matches(":hover") || (keyboardNavigation && document.activeElement === usageAnchor)) return;
    activeUsageId = undefined;
    usageAnchor = undefined;
    removePopup("usage-popup");
    setUsagePopupActive(false);
    vscode.postMessage({ type: "reset-view-heading" });
  }, 220);
}

function renderUsagePopup(): void {
  removePopup("usage-popup");
  setUsagePopupActive(false);
  const provider = state.providers.find((candidate) => candidate.id === activeUsageId);
  if (!provider || !usageAnchor) {
    vscode.postMessage({ type: "reset-view-heading" });
    return;
  }
  const popup = element("section", "popup usage-popup");
  const usage = providerUsage.get(provider.id);
  updateUsageViewHeading(usage);
  if (!usage) {
    popup.append(notice("Loading usage limits...", "usage-message"));
  } else if (usage.status !== "available" || (!usage.fiveHour && !usage.weekly)) {
    popup.append(notice(usage.message ?? "Usage limits are not available.", "usage-message"));
  } else {
    if (usage.fiveHour) popup.append(usageRow("5-hour limit", usage.fiveHour.usedPercent, usage.fiveHour.resetsAtEpochSeconds));
    if (usage.weekly) popup.append(usageRow("Weekly limit", usage.weekly.usedPercent, usage.weekly.resetsAtEpochSeconds));
  }
  popup.addEventListener("pointerenter", () => {
    if (hideUsageTimer !== undefined) window.clearTimeout(hideUsageTimer);
  });
  popup.addEventListener("pointerleave", scheduleHideUsage);
  popup.style.visibility = "hidden";
  overlayRoot.append(popup);
  setUsagePopupActive(true);
  positionPopup(popup, usageAnchor, "above");
  popup.style.visibility = "visible";
}

function updateUsageViewHeading(usage: ProviderUsageView | undefined): void {
  if (!usage) {
    vscode.postMessage({ type: "show-usage-heading", details: "Loading..." });
    return;
  }
  const details: string[] = [];
  if (usage.projectTokenTotal !== null && usage.projectTokenTotal !== undefined) {
    details.push(`${formatCompact(usage.projectTokenTotal)} tokens`);
  }
  if (usage.resetCount !== undefined) {
    details.push(`${usage.resetCount} ${usage.resetCount === 1 ? "reset" : "resets"}`);
  }
  vscode.postMessage({
    type: "show-usage-heading",
    details: details.join(" · ") || "Limits unavailable"
  });
}

function setUsagePopupActive(active: boolean): void {
  document.querySelector<HTMLElement>(".toolbar")?.classList.toggle("usage-popup-active", active);
}

function usageRow(label: string, usedPercent: number, resetsAt?: number): HTMLElement {
  const row = element("div", "usage-row");
  const line = element("div", "usage-line");
  const labelNode = element("span", "usage-label");
  labelNode.textContent = label;
  const reset = element("span", "usage-reset");
  reset.textContent = resetsAt ? formatResetTime(resetsAt) : "";
  reset.title = reset.textContent;
  const left = element("span", "usage-left");
  left.textContent = `${Math.max(0, 100 - Math.round(usedPercent))}% left`;
  const bar = element("div", "usage-bar");
  const fill = document.createElement("span");
  fill.style.width = `${clamp(usedPercent, 0, 100)}%`;
  bar.append(fill);
  line.append(labelNode, reset, left);
  row.append(line, bar);
  return row;
}

function positionPopup(popup: HTMLElement, anchor: HTMLElement, preference: "left" | "above"): void {
  const rect = anchor.getBoundingClientRect();
  const popupRect = popup.getBoundingClientRect();
  const horizontalMargin = preference === "above" ? 4 : 8;
  const verticalMargin = preference === "above" ? 0 : 8;
  const verticalGap = preference === "above" ? 3 : 12;
  let left = preference === "left" ? rect.left - popupRect.width - 8 : rect.left;
  let top = preference === "above" ? rect.top - popupRect.height - verticalGap : rect.top - verticalGap;
  if (left < horizontalMargin) left = Math.min(window.innerWidth - popupRect.width - horizontalMargin, rect.right + 8);
  if (left + popupRect.width > window.innerWidth - horizontalMargin) left = window.innerWidth - popupRect.width - horizontalMargin;
  if (top < verticalMargin) top = verticalMargin;
  if (top + popupRect.height > window.innerHeight - verticalMargin) top = window.innerHeight - popupRect.height - verticalMargin;
  popup.style.left = `${Math.max(horizontalMargin, left)}px`;
  popup.style.top = `${Math.max(verticalMargin, top)}px`;
}

function providerFor(id: ProviderId): DashboardProviderView | undefined {
  return state.providers.find((provider) => provider.id === id);
}

function terminalTaskStateLabel(taskState: SessionCardView["taskState"]): string {
  if (taskState === "working") return "AI is working";
  if (taskState === "ready") return "Ready for review";
  return "AI is idle";
}

function providerImage(provider: DashboardProviderView, className: string): HTMLImageElement {
  const image = document.createElement("img");
  image.className = className;
  image.src = provider.iconUri;
  image.alt = provider.displayName;
  image.draggable = false;
  return image;
}

function notice(text: string, className = "conversation-notice"): HTMLElement {
  const node = element("div", className);
  node.textContent = text;
  return node;
}

function emptyState(title: string, message: string): HTMLElement {
  const root = element("div", "empty-state");
  const content = document.createElement("div");
  const heading = document.createElement("strong");
  heading.textContent = title;
  const description = document.createElement("span");
  description.textContent = message;
  content.append(heading, description);
  root.append(content);
  return root;
}

function skeleton(): HTMLElement {
  const root = element("div", "skeleton");
  root.append(element("div", "skeleton-card"), element("div", "skeleton-card"), element("div", "skeleton-card"));
  return root;
}

function showToast(message: string): void {
  document.querySelector(".toast")?.remove();
  if (toastTimer !== undefined) window.clearTimeout(toastTimer);
  const toast = element("div", "toast");
  toast.textContent = message;
  document.body.append(toast);
  toastTimer = window.setTimeout(() => toast.remove(), 5_000);
}

function removePopup(className: string): void {
  overlayRoot.querySelector(`.${className}`)?.remove();
}

function formatCompact(value: number): string {
  const absolute = Math.abs(value);
  if (absolute >= 1_000_000_000) return `${trimDecimal(value / 1_000_000_000)}B`;
  if (absolute >= 1_000_000) return `${trimDecimal(value / 1_000_000)}M`;
  if (absolute >= 1_000) return `${trimDecimal(value / 1_000)}K`;
  return Math.round(value).toLocaleString();
}

function trimDecimal(value: number): string {
  return value >= 100 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}

function formatSeconds(value: number | null): string {
  if (value === null) return "—";
  const seconds = value / 1_000;
  return `${seconds.toFixed(1).replace(/\.0$/, "")}S`;
}

function formatResetTime(epochSeconds: number): string {
  const value = new Date(epochSeconds * 1_000);
  const now = new Date();
  const sameDay = value.toDateString() === now.toDateString();
  return sameDay
    ? value.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
    : value.toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

function button(label: string, action: () => void, className: string): HTMLButtonElement {
  const control = element("button", className);
  control.type = "button";
  control.textContent = label;
  control.addEventListener("click", action);
  return control;
}

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  node.className = className;
  return node;
}

function requiredElement(selector: string): HTMLElement {
  const target = document.querySelector<HTMLElement>(selector);
  if (!target) throw new Error(`Missing element: ${selector}`);
  return target;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

vscode.postMessage({ type: "ready" });
render();
