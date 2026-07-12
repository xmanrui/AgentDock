import * as vscode from "vscode";
import { configuredProviders } from "./config.js";
import type { AgentSession, CliProvider } from "./core/model.js";
import { SessionDiscoveryService } from "./services/sessionDiscovery.js";

interface DashboardMessage {
  type: "ready" | "refresh" | "openSettings";
}

interface DashboardPayload {
  type: "state";
  projectPath: string | null;
  loading: boolean;
  error?: string;
  providers: Array<Pick<CliProvider, "id" | "displayName" | "enabled">>;
  sessions: AgentSession[];
}

class SessionsViewProvider implements vscode.WebviewViewProvider, vscode.Disposable {
  private readonly discovery = new SessionDiscoveryService();
  private readonly disposables: vscode.Disposable[] = [];
  private view?: vscode.WebviewView;

  constructor(private readonly extensionUri: vscode.Uri) {
    this.disposables.push(
      vscode.workspace.onDidChangeWorkspaceFolders(() => void this.refresh()),
      vscode.workspace.onDidChangeConfiguration((event) => {
        if (event.affectsConfiguration("agentdock")) {
          this.discovery.clearCache();
          void this.refresh();
        }
      })
    );
  }

  resolveWebviewView(view: vscode.WebviewView): void {
    this.view = view;
    view.webview.options = {
      enableScripts: true,
      localResourceRoots: [vscode.Uri.joinPath(this.extensionUri, "dist")]
    };
    view.webview.html = this.html(view.webview);
    this.disposables.push(
      view.webview.onDidReceiveMessage((message: DashboardMessage) => {
        if (message.type === "ready" || message.type === "refresh") {
          void this.refresh(message.type === "refresh");
        } else if (message.type === "openSettings") {
          void vscode.commands.executeCommand("workbench.action.openSettings", "@ext:xmanrui.agentdock");
        }
      })
    );
  }

  async refresh(clearCache = false): Promise<void> {
    const view = this.view;
    if (!view) return;
    if (clearCache) this.discovery.clearCache();

    const providers = configuredProviders();
    const projectPath = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? null;
    await this.postState({ projectPath, loading: true, providers, sessions: [] });
    if (!projectPath) {
      await this.postState({ projectPath, loading: false, providers, sessions: [] });
      return;
    }

    try {
      const sessions = await this.discovery.discover(projectPath, providers);
      await this.postState({ projectPath, loading: false, providers, sessions });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      await this.postState({ projectPath, loading: false, providers, sessions: [], error: message });
    }
  }

  dispose(): void {
    for (const disposable of this.disposables.splice(0)) disposable.dispose();
  }

  private async postState(payload: Omit<DashboardPayload, "type">): Promise<void> {
    await this.view?.webview.postMessage({ type: "state", ...payload } satisfies DashboardPayload);
  }

  private html(webview: vscode.Webview): string {
    const nonce = randomNonce();
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, "dist", "webview.js"));
    return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <title>AgentDock</title>
</head>
<body>
  <main id="app" aria-live="polite"></main>
  <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }
}

export function activate(context: vscode.ExtensionContext): void {
  const provider = new SessionsViewProvider(context.extensionUri);
  context.subscriptions.push(
    provider,
    vscode.window.registerWebviewViewProvider("agentdock.sessions", provider),
    vscode.commands.registerCommand("agentdock.refreshSessions", () => provider.refresh(true)),
    vscode.commands.registerCommand("agentdock.openSettings", () =>
      vscode.commands.executeCommand("workbench.action.openSettings", "@ext:xmanrui.agentdock")
    )
  );
}

export function deactivate(): void {}

function randomNonce(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  return Array.from({ length: 32 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}
