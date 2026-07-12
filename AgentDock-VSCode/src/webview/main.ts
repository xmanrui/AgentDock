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

const style = document.createElement("style");
style.textContent = dashboardStyles;
document.head.append(style);

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
  const control = button("", () => {
    providerFilter = provider.id;
    render();
    requestAnimationFrame(() => {
      document.querySelector<HTMLButtonElement>(`.filter-button[data-provider-id="${provider.id}"]`)?.focus();
    });
  }, `filter-button${providerFilter === provider.id ? " active" : ""}`);
  control.dataset.providerId = provider.id;
  control.append(providerImage(provider, "provider-logo"));
  control.title = `${provider.displayName} (${count})`;
  control.setAttribute("aria-label", `${provider.displayName}, ${count} sessions`);
  control.setAttribute("aria-pressed", String(providerFilter === provider.id));
  control.addEventListener("pointerenter", () => showUsage(provider.id, control));
  control.addEventListener("pointerleave", scheduleHideUsage);
  control.addEventListener("focus", () => showUsage(provider.id, control));
  control.addEventListener("blur", scheduleHideUsage);
  if (activeUsageId === provider.id) usageAnchor = control;
  return control;
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
  if (provider) header.append(providerImage(provider, "session-provider-logo"));
  else header.append(element("span", "session-provider-logo"));
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
  if (session.taskState !== "idle") {
    const label = element("span", `task-label ${session.taskState}`);
    label.textContent = session.taskState === "working" ? "Working" : "Ready";
    status.append(label);
  }
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
  if (session.taskState === "working" && session.liveText) {
    const live = element("div", "live-output");
    const track = element("div", "live-track");
    const text = element("span", "live-text");
    text.textContent = session.liveText;
    track.append(text);
    live.append(track);
    live.title = session.liveText;
    card.append(live);
  }

  const footer = element("footer", "session-footer");
  const updated = element("time", "updated");
  updated.dateTime = new Date(session.updatedAt).toISOString();
  updated.textContent = session.updatedLabel;
  const open = button("Open", () => vscode.postMessage({ type: "open-session", sessionId: session.id, yolo: false }), "action-button");
  const yolo = button("YOLO", () => vscode.postMessage({ type: "open-session", sessionId: session.id, yolo: true }), "action-button yolo");
  yolo.title = "Open with provider-specific permission bypass flags";
  const pin = button(session.pinned ? "Unpin" : "Pin", () => vscode.postMessage({ type: "toggle-pin", sessionId: session.id }), "action-button");
  footer.append(updated, open, yolo, pin);
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

function sparkline(rawValues: readonly (number | null)[], available: boolean, label: string): SVGSVGElement {
  const values = Array.from({ length: 7 }, (_, index) => Math.max(0, rawValues[index] ?? 0));
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.classList.add("sparkline");
  svg.setAttribute("viewBox", "0 0 150 43");
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", `${label}, oldest to today: ${values.join(", ")}`);
  const points = chartPoints(values);
  const pathData = smoothPath(points);
  const gradientId = `gradient-${Math.random().toString(36).slice(2)}`;
  const defs = document.createElementNS(svg.namespaceURI, "defs");
  const gradient = document.createElementNS(svg.namespaceURI, "linearGradient");
  gradient.id = gradientId;
  gradient.setAttribute("x1", "0");
  gradient.setAttribute("y1", "0");
  gradient.setAttribute("x2", "0");
  gradient.setAttribute("y2", "1");
  const top = document.createElementNS(svg.namespaceURI, "stop");
  top.setAttribute("offset", "0");
  top.setAttribute("stop-color", "var(--ad-accent)");
  top.setAttribute("stop-opacity", available ? ".24" : "0");
  const bottom = document.createElementNS(svg.namespaceURI, "stop");
  bottom.setAttribute("offset", "1");
  bottom.setAttribute("stop-color", "var(--ad-accent)");
  bottom.setAttribute("stop-opacity", "0");
  gradient.append(top, bottom);
  defs.append(gradient);
  svg.append(defs);
  if (available) {
    const area = document.createElementNS(svg.namespaceURI, "path");
    area.setAttribute("d", `${pathData} L ${points.at(-1)?.x ?? 145} 39 L ${points[0]?.x ?? 5} 39 Z`);
    area.setAttribute("fill", `url(#${gradientId})`);
    svg.append(area);
  }
  const line = document.createElementNS(svg.namespaceURI, "path");
  line.setAttribute("d", pathData);
  line.setAttribute("fill", "none");
  line.setAttribute("stroke", available ? "var(--ad-accent)" : "var(--ad-muted)");
  line.setAttribute("stroke-opacity", available ? "1" : ".55");
  line.setAttribute("stroke-width", available ? "2" : "1.2");
  line.setAttribute("stroke-linecap", "round");
  line.setAttribute("stroke-linejoin", "round");
  if (!available) line.setAttribute("stroke-dasharray", "3 3");
  svg.append(line);
  points.forEach((point, index) => {
    const dot = document.createElementNS(svg.namespaceURI, "circle");
    dot.setAttribute("cx", String(point.x));
    dot.setAttribute("cy", String(point.y));
    dot.setAttribute("r", available ? "1.7" : "1.2");
    dot.setAttribute("fill", available ? "var(--ad-accent)" : "var(--ad-muted)");
    dot.setAttribute("fill-opacity", rawValues[index] === null ? ".38" : "1");
    svg.append(dot);
  });
  return svg;
}

function chartPoints(values: readonly number[]): Array<{ x: number; y: number }> {
  const maximum = Math.max(...values);
  const minimum = Math.min(...values);
  const range = maximum - minimum;
  return values.map((value, index) => ({
    x: 5 + index * (140 / Math.max(1, values.length - 1)),
    y: range === 0 ? 31 : 36 - ((value - minimum) / range) * 29
  }));
}

function smoothPath(points: readonly { x: number; y: number }[]): string {
  if (!points.length) return "";
  let path = `M ${points[0]?.x ?? 0} ${points[0]?.y ?? 0}`;
  for (let index = 0; index < points.length - 1; index += 1) {
    const previous = points[Math.max(0, index - 1)] ?? points[index];
    const current = points[index];
    const next = points[index + 1];
    const after = points[Math.min(points.length - 1, index + 2)] ?? next;
    if (!previous || !current || !next || !after) continue;
    const tension = 0.18;
    const control1 = { x: current.x + (next.x - previous.x) * tension, y: current.y + (next.y - previous.y) * tension };
    const control2 = { x: next.x - (after.x - current.x) * tension, y: next.y - (after.y - current.y) * tension };
    path += ` C ${control1.x} ${clamp(control1.y, 5, 38)}, ${control2.x} ${clamp(control2.y, 5, 38)}, ${next.x} ${next.y}`;
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
    activeUsageId = undefined;
    usageAnchor = undefined;
    removePopup("usage-popup");
  }, 220);
}

function renderUsagePopup(): void {
  removePopup("usage-popup");
  const provider = state.providers.find((candidate) => candidate.id === activeUsageId);
  if (!provider || !usageAnchor) return;
  const popup = element("section", "popup usage-popup");
  const header = element("header", "usage-header");
  header.append(providerImage(provider, "provider-logo"));
  const heading = document.createElement("strong");
  heading.textContent = "Usage";
  header.append(heading);
  const usage = providerUsage.get(provider.id);
  if (usage?.projectTokenTotal !== null && usage?.projectTokenTotal !== undefined) header.append(pill(`${formatCompact(usage.projectTokenTotal)} tokens`));
  if (usage?.resetCount !== undefined) header.append(pill(`${usage.resetCount} ${usage.resetCount === 1 ? "reset" : "resets"}`));
  popup.append(header);
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
  overlayRoot.append(popup);
  positionPopup(popup, usageAnchor, "above");
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
  const margin = 8;
  let left = preference === "left" ? rect.left - popupRect.width - 8 : rect.left;
  let top = preference === "above" ? rect.top - popupRect.height - 7 : rect.top - 12;
  if (left < margin) left = Math.min(window.innerWidth - popupRect.width - margin, rect.right + 8);
  if (left + popupRect.width > window.innerWidth - margin) left = window.innerWidth - popupRect.width - margin;
  if (top < margin) top = preference === "above" ? rect.bottom + 7 : margin;
  if (top + popupRect.height > window.innerHeight - margin) top = window.innerHeight - popupRect.height - margin;
  popup.style.left = `${Math.max(margin, left)}px`;
  popup.style.top = `${Math.max(margin, top)}px`;
}

function providerFor(id: ProviderId): DashboardProviderView | undefined {
  return state.providers.find((provider) => provider.id === id);
}

function providerImage(provider: DashboardProviderView, className: string): HTMLImageElement {
  const image = document.createElement("img");
  image.className = className;
  image.src = provider.iconUri;
  image.alt = provider.displayName;
  image.draggable = false;
  return image;
}

function pill(text: string): HTMLElement {
  const node = element("span", "usage-pill");
  node.textContent = text;
  return node;
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
  return `${seconds >= 10 ? Math.round(seconds) : seconds.toFixed(1).replace(/\.0$/, "")}S`;
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
