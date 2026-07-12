interface SessionView {
  id: string;
  name: string;
  summary: string;
  providerId: "codex" | "claude-code" | "gemini";
  updatedAt: number;
}

interface ProviderView {
  id: SessionView["providerId"];
  displayName: string;
  enabled: boolean;
}

interface DashboardState {
  type: "state";
  projectPath: string | null;
  loading: boolean;
  error?: string;
  providers: ProviderView[];
  sessions: SessionView[];
}

interface VsCodeApi {
  postMessage(message: { type: "ready" | "refresh" | "openSettings" }): void;
}

declare function acquireVsCodeApi(): VsCodeApi;

const vscode = acquireVsCodeApi();
const app = document.querySelector<HTMLElement>("#app");
let state: DashboardState = {
  type: "state",
  projectPath: null,
  loading: true,
  providers: [],
  sessions: []
};
let query = "";
let providerFilter: "all" | SessionView["providerId"] = "all";

window.addEventListener("message", (event: MessageEvent<DashboardState>) => {
  if (event.data.type !== "state") return;
  state = event.data;
  render();
});

function render(): void {
  if (!app) return;
  app.replaceChildren();
  app.append(styles(), toolbar());

  if (state.loading) {
    app.append(empty("Loading sessions..."));
    return;
  }
  if (state.error) {
    app.append(empty(`Unable to load sessions: ${state.error}`));
    return;
  }
  if (!state.projectPath) {
    app.append(empty("Open a folder to view its AI CLI sessions."));
    return;
  }

  const normalizedQuery = query.trim().toLocaleLowerCase();
  const sessions = state.sessions.filter((session) => {
    const providerMatches = providerFilter === "all" || session.providerId === providerFilter;
    const queryMatches =
      !normalizedQuery || `${session.name} ${session.summary}`.toLocaleLowerCase().includes(normalizedQuery);
    return providerMatches && queryMatches;
  });
  const list = element("section", "sessions");
  for (const session of sessions) list.append(sessionCard(session));
  app.append(list, sessions.length ? document.createTextNode("") : empty("No matching sessions."));
}

function toolbar(): HTMLElement {
  const container = element("header", "toolbar");
  const search = document.createElement("input");
  search.type = "search";
  search.placeholder = "Search sessions";
  search.value = query;
  search.addEventListener("input", () => {
    query = search.value;
    render();
    requestAnimationFrame(() => document.querySelector<HTMLInputElement>('input[type="search"]')?.focus());
  });

  const filters = element("div", "filters");
  filters.append(filterButton("all", `All ${state.sessions.length}`));
  for (const provider of state.providers.filter((candidate) => candidate.enabled)) {
    filters.append(
      filterButton(
        provider.id,
        `${provider.displayName} ${state.sessions.filter((session) => session.providerId === provider.id).length}`
      )
    );
  }
  const refresh = button("Refresh", () => vscode.postMessage({ type: "refresh" }));
  refresh.className = "refresh";
  container.append(search, filters, refresh);
  return container;
}

function filterButton(id: typeof providerFilter, label: string): HTMLButtonElement {
  const control = button(label, () => {
    providerFilter = id;
    render();
  });
  control.className = id === providerFilter ? "filter active" : "filter";
  return control;
}

function sessionCard(session: SessionView): HTMLElement {
  const card = element("article", "session-card");
  const heading = element("div", "session-heading");
  const provider = element("span", `provider ${session.providerId}`);
  provider.textContent = providerLabel(session.providerId);
  const title = document.createElement("strong");
  title.textContent = session.name;
  heading.append(provider, title);
  const summary = element("p", "summary");
  summary.textContent = session.summary || "No preview available";
  const updated = element("time", "updated");
  updated.dateTime = new Date(session.updatedAt).toISOString();
  updated.textContent = relativeTime(session.updatedAt);
  card.append(heading, summary, updated);
  return card;
}

function providerLabel(provider: SessionView["providerId"]): string {
  if (provider === "claude-code") return "C";
  if (provider === "gemini") return "G";
  return "O";
}

function relativeTime(timestamp: number): string {
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60_000));
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  return `${Math.floor(hours / 24)} days ago`;
}

function empty(message: string): HTMLElement {
  const node = element("p", "empty");
  node.textContent = message;
  return node;
}

function button(label: string, action: () => void): HTMLButtonElement {
  const control = document.createElement("button");
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

function styles(): HTMLStyleElement {
  const sheet = document.createElement("style");
  sheet.textContent = `
    :root { color-scheme: light dark; }
    * { box-sizing: border-box; }
    body { margin: 0; color: var(--vscode-foreground); background: var(--vscode-sideBar-background); font: var(--vscode-font-size)/1.45 var(--vscode-font-family); }
    button, input { font: inherit; }
    .toolbar { position: sticky; top: 0; z-index: 2; display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 12px; background: var(--vscode-sideBar-background); border-bottom: 1px solid var(--vscode-sideBarSectionHeader-border); }
    input { grid-column: 1 / -1; width: 100%; height: 32px; padding: 0 9px; color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border, transparent); outline: none; }
    input:focus { border-color: var(--vscode-focusBorder); }
    .filters { display: flex; min-width: 0; gap: 5px; overflow-x: auto; }
    button { min-height: 28px; padding: 3px 9px; color: var(--vscode-button-secondaryForeground); background: var(--vscode-button-secondaryBackground); border: 1px solid transparent; border-radius: 4px; cursor: pointer; white-space: nowrap; }
    button:hover { background: var(--vscode-button-secondaryHoverBackground); }
    .filter.active { color: var(--vscode-button-foreground); background: var(--vscode-button-background); border-color: var(--vscode-focusBorder); }
    .sessions { display: grid; gap: 8px; padding: 12px; }
    .session-card { min-width: 0; padding: 12px; background: var(--vscode-sideBarSectionHeader-background); border: 1px solid var(--vscode-sideBarSectionHeader-border); border-radius: 6px; }
    .session-heading { display: flex; align-items: center; min-width: 0; gap: 9px; }
    .session-heading strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .provider { display: grid; flex: 0 0 25px; width: 25px; height: 25px; place-items: center; border: 1px solid var(--vscode-widget-border); border-radius: 50%; font-weight: 700; }
    .provider.claude-code { color: var(--vscode-charts-orange); }
    .provider.gemini { color: var(--vscode-charts-blue); }
    .summary { display: -webkit-box; margin: 10px 0; overflow: hidden; color: var(--vscode-descriptionForeground); -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
    .updated { color: var(--vscode-descriptionForeground); font-size: 0.9em; }
    .empty { margin: 0; padding: 40px 18px; color: var(--vscode-descriptionForeground); text-align: center; }
  `;
  return sheet;
}

vscode.postMessage({ type: "ready" });
render();
