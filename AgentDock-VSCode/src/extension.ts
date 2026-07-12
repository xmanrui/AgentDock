import * as vscode from "vscode";
import { configuredProviders, previewMessageLimit, refreshIntervalMillis } from "./config.js";
import { fileFingerprint } from "./core/files.js";
import type { AgentSession, CliProvider, ProviderId, SessionCardView } from "./core/model.js";
import { formatRelativeTime } from "./core/text.js";
import { ProjectStateStore, sessionComparator } from "./services/projectState.js";
import { ProviderUsageService } from "./services/providerUsage.js";
import { SessionContentService } from "./services/sessionContent.js";
import { SessionDiscoveryService } from "./services/sessionDiscovery.js";
import { SessionMetricsService } from "./services/sessionMetrics.js";
import { TerminalManager } from "./terminal/terminalManager.js";
import type {
  DashboardProviderView,
  DashboardViewState,
  ExtensionToWebviewMessage,
  WebviewToExtensionMessage
} from "./webview/protocol.js";

class SessionsViewProvider implements vscode.WebviewViewProvider, vscode.Disposable {
  private readonly discovery = new SessionDiscoveryService();
  private readonly content = new SessionContentService(undefined, previewMessageLimit());
  private readonly metrics = new SessionMetricsService(this.content);
  private readonly usage = new ProviderUsageService();
  private readonly store: ProjectStateStore;
  private readonly terminal: TerminalManager;
  private readonly disposables: vscode.Disposable[] = [];
  private view?: vscode.WebviewView;
  private refreshTimer?: NodeJS.Timeout;
  private sessions: AgentSession[] = [];
  private cards: SessionCardView[] = [];
  private providers: CliProvider[] = [];
  private projectPath: string | null = null;
  private loading = true;
  private refreshing = false;
  private error?: string;
  private refreshGeneration = 0;

  constructor(private readonly context: vscode.ExtensionContext) {
    this.store = new ProjectStateStore(context.workspaceState);
    this.terminal = new TerminalManager(providerIconUris(context.extensionUri));
    this.disposables.push(
      this.terminal,
      this.terminal.onDidChangeSessionRuntime(() => {
        this.rebuildCards();
        void this.postState();
      }),
      vscode.workspace.onDidChangeWorkspaceFolders(() => void this.refresh(true)),
      vscode.workspace.onDidChangeConfiguration((event) => {
        if (!event.affectsConfiguration("agentdock")) return;
        this.discovery.clearCache();
        this.content.setRecentMessageLimit(previewMessageLimit());
        this.content.clearCache();
        this.metrics.clearCache();
        this.usage.invalidate();
        this.scheduleRefresh();
        void this.refresh(true);
      })
    );
  }

  resolveWebviewView(view: vscode.WebviewView): void {
    this.view = view;
    this.resetViewHeading();
    view.webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(this.context.extensionUri, "dist"),
        vscode.Uri.joinPath(this.context.extensionUri, "media")
      ]
    };
    view.webview.html = this.html(view.webview);
    this.disposables.push(
      view.webview.onDidReceiveMessage((message: WebviewToExtensionMessage) => void this.handleMessage(message)),
      view.onDidChangeVisibility(() => {
        if (!view.visible) this.resetViewHeading();
        this.scheduleRefresh();
        if (view.visible) void this.refresh(false);
      })
    );
    this.scheduleRefresh();
  }

  async refresh(force = false): Promise<void> {
    const generation = ++this.refreshGeneration;
    this.providers = configuredProviders();
    this.projectPath = currentProjectPath();
    this.error = undefined;
    this.loading = this.sessions.length === 0;
    this.refreshing = !this.loading;

    if (!this.projectPath) {
      this.sessions = [];
      this.cards = [];
      this.loading = false;
      this.refreshing = false;
      await this.postState();
      return;
    }

    const persisted = this.store.sessions(this.projectPath);
    if (!this.sessions.length && persisted.length) {
      this.sessions = this.enabledSessions(persisted);
      await this.restorePersistedMetrics(this.sessions);
      this.rebuildCards();
    }
    await this.postState();

    try {
      if (force) this.discovery.clearCache();
      const discovered = await this.discovery.discover(this.projectPath, this.providers);
      if (generation !== this.refreshGeneration) return;
      this.sessions = this.enabledSessions(await this.store.mergeDiscovered(this.projectPath, discovered));
      const sessionsNeedingMetrics = await this.restorePersistedMetrics(this.sessions);
      this.loading = false;
      this.refreshing = false;
      this.rebuildCards();
      await this.postState();

      const metricCacheUpdates: Array<Parameters<ProjectStateStore["saveMetricsBatch"]>[0][number]> = [];
      await mapWithConcurrency(sessionsNeedingMetrics, 4, async (session) => {
        const metrics = await this.metrics.load(session);
        const fingerprint = await fileFingerprint(session.historyFilePath);
        if (fingerprint) {
          metricCacheUpdates.push([session.id, {
            metrics,
            sourceModifiedAt: fingerprint.modifiedAt,
            sourceLength: fingerprint.size,
            anchorDate: this.metrics.currentAnchorDate()
          }]);
        }
      });
      await this.store.saveMetricsBatch(metricCacheUpdates);
      if (generation !== this.refreshGeneration) return;
      this.rebuildCards();
      await this.postState();
    } catch (error) {
      if (generation !== this.refreshGeneration) return;
      this.loading = false;
      this.refreshing = false;
      this.error = error instanceof Error ? error.message : String(error);
      await this.postState();
    }
  }

  dispose(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    for (const disposable of this.disposables.splice(0)) disposable.dispose();
  }

  private async handleMessage(message: WebviewToExtensionMessage): Promise<void> {
    if (message.type === "ready") {
      await this.refresh(false);
      return;
    }
    if (message.type === "refresh") {
      this.usage.invalidate();
      await this.refresh(true);
      return;
    }
    if (message.type === "open-settings") {
      await vscode.commands.executeCommand("workbench.action.openSettings", "@ext:xmanrui.agentdock");
      return;
    }
    if (message.type === "show-usage-heading") {
      if (this.view) {
        this.view.title = message.details ? `Usage · ${message.details}` : "Usage";
        this.view.description = undefined;
      }
      return;
    }
    if (message.type === "reset-view-heading") {
      this.resetViewHeading();
      return;
    }
    if (message.type === "toggle-pin") {
      await this.store.togglePin(message.sessionId);
      const session = this.sessions.find((candidate) => candidate.id === message.sessionId);
      if (session) session.pinned = !session.pinned;
      this.sessions.sort(sessionComparator);
      this.rebuildCards();
      await this.postState();
      return;
    }
    if (message.type === "load-preview") {
      const session = this.sessions.find((candidate) => candidate.id === message.sessionId);
      if (session) await this.post({ type: "session-preview", sessionId: session.id, preview: await this.content.load(session) });
      return;
    }
    if (message.type === "load-provider-usage") {
      const provider = this.providers.find((candidate) => candidate.id === message.providerId);
      if (!provider) return;
      const snapshot = await this.usage.load(provider);
      await this.post({
        type: "provider-usage",
        usage: {
          ...snapshot,
          projectTokenTotal: this.metrics.cachedProviderTotal(this.sessions, provider.id)
        }
      });
      return;
    }
    if (message.type === "open-session") {
      await this.openSession(message.sessionId, message.yolo);
    }
  }

  private async openSession(sessionId: string, yolo: boolean): Promise<void> {
    const session = this.sessions.find((candidate) => candidate.id === sessionId);
    const provider = session && this.providers.find((candidate) => candidate.id === session.providerId);
    if (!session || !provider || !this.projectPath) {
      await this.post({ type: "action-error", message: "The selected session is no longer available." });
      return;
    }
    const result = await this.terminal.launch(session, provider, this.projectPath, yolo);
    if (!result.ok) {
      const message = result.message ?? "Could not open the session.";
      await this.post({ type: "action-error", message });
      void vscode.window.showErrorMessage(`AgentDock: ${message}`);
    } else if (result.fallback && result.message) {
      void vscode.window.showInformationMessage(`AgentDock: ${result.message}`);
    }
    this.rebuildCards();
    await this.postState();
  }

  private resetViewHeading(): void {
    if (!this.view) return;
    this.view.title = "Sessions";
    this.view.description = undefined;
  }

  private enabledSessions(sessions: AgentSession[]): AgentSession[] {
    const enabled = new Set(this.providers.filter((provider) => provider.enabled).map((provider) => provider.id));
    return sessions.filter((session) => enabled.has(session.providerId)).sort(sessionComparator);
  }

  private async restorePersistedMetrics(sessions: readonly AgentSession[]): Promise<AgentSession[]> {
    const needsMetrics: AgentSession[] = [];
    const anchorDate = this.metrics.currentAnchorDate();
    await mapWithConcurrency(sessions, 8, async (session) => {
      const persisted = this.store.metrics(session.id);
      const fingerprint = await fileFingerprint(session.historyFilePath);
      if (
        persisted &&
        fingerprint &&
        persisted.anchorDate === anchorDate &&
        persisted.sourceModifiedAt === fingerprint.modifiedAt &&
        persisted.sourceLength === fingerprint.size
      ) {
        this.metrics.prime(session, persisted.metrics, anchorDate);
      } else {
        needsMetrics.push(session);
      }
    });
    return needsMetrics;
  }

  private rebuildCards(): void {
    const providerNames = new Map(this.providers.map((provider) => [provider.id, provider.displayName]));
    this.cards = this.sessions.map((session) => ({
      ...session,
      ...this.metrics.cached(session),
      ...this.terminal.runtime(session.id),
      providerName: providerNames.get(session.providerId) ?? session.providerId,
      updatedLabel: formatRelativeTime(session.updatedAt, Date.now(), vscode.env.language)
    }));
  }

  private async postState(): Promise<void> {
    const message: DashboardViewState = {
      type: "dashboard-state",
      projectPath: this.projectPath,
      loading: this.loading,
      refreshing: this.refreshing,
      error: this.error,
      providers: this.providerViews(),
      sessions: this.cards
    };
    await this.post(message);
  }

  private providerViews(): DashboardProviderView[] {
    const webview = this.view?.webview;
    return this.providers.map((provider) => ({
      id: provider.id,
      displayName: provider.displayName,
      enabled: provider.enabled,
      iconUri: webview ? webview.asWebviewUri(this.providerIcons()[provider.id]).toString() : ""
    }));
  }

  private providerIcons(): Readonly<Record<ProviderId, vscode.Uri>> {
    return providerIconUris(this.context.extensionUri);
  }

  private async post(message: ExtensionToWebviewMessage): Promise<void> {
    await this.view?.webview.postMessage(message);
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    this.refreshTimer = undefined;
    if (!this.view?.visible) return;
    this.refreshTimer = setInterval(() => void this.refresh(false), refreshIntervalMillis());
  }

  private html(webview: vscode.Webview): string {
    const nonce = randomNonce();
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.context.extensionUri, "dist", "webview.js"));
    return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource} data:; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <title>AgentDock</title>
</head>
<body>
  <main id="app" aria-live="polite"></main>
  <div id="overlay-root"></div>
  <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }
}

export function activate(context: vscode.ExtensionContext): void {
  const provider = new SessionsViewProvider(context);
  context.subscriptions.push(
    provider,
    vscode.window.registerWebviewViewProvider("agentdock.sessions", provider, { webviewOptions: { retainContextWhenHidden: true } }),
    vscode.commands.registerCommand("agentdock.refreshSessions", () => provider.refresh(true)),
    vscode.commands.registerCommand("agentdock.openSettings", () =>
      vscode.commands.executeCommand("workbench.action.openSettings", "@ext:xmanrui.agentdock")
    )
  );
}

export function deactivate(): void {}

function currentProjectPath(): string | null {
  return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? null;
}

function providerIconUris(extensionUri: vscode.Uri): Readonly<Record<ProviderId, vscode.Uri>> {
  return {
    codex: vscode.Uri.joinPath(extensionUri, "media", "codex.svg"),
    "claude-code": vscode.Uri.joinPath(extensionUri, "media", "claude.svg"),
    gemini: vscode.Uri.joinPath(extensionUri, "media", "gemini.svg")
  };
}

async function mapWithConcurrency<T>(items: readonly T[], concurrency: number, operation: (item: T) => Promise<void>): Promise<void> {
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const item = items[nextIndex];
      nextIndex += 1;
      if (item !== undefined) await operation(item);
    }
  });
  await Promise.all(workers);
}

function randomNonce(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  return Array.from({ length: 32 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}
