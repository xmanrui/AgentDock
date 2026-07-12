import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm } from "node:fs/promises";
import * as path from "node:path";
import * as vscode from "vscode";
import { renderCommand } from "../core/commandRenderer.js";
import { directoryExists, fileFingerprint } from "../core/files.js";
import type {
  AgentSession,
  CliProvider,
  ProviderId,
  SessionRuntimeState,
  TerminalTaskState
} from "../core/model.js";
import { ProviderDetectionService } from "../services/providerDetection.js";
import { LocalTerminalActivityMonitor } from "./activity.js";
import { exitMarkerPath, wrapWithExitMarker } from "./exitMarker.js";
import { TerminalOutputBuffer } from "./streamText.js";
import { reduceTerminalTaskState } from "./taskState.js";

interface ManagedTerminal {
  token: string;
  terminal: vscode.Terminal;
  sessionId: string;
  sessionName: string;
  providerId: ProviderId;
  expectedCommand: string;
  terminalOpen: boolean;
  taskState: TerminalTaskState;
  liveText?: string;
  changedAt: number;
  execution?: vscode.TerminalShellExecution;
  monitor: LocalTerminalActivityMonitor;
  output: TerminalOutputBuffer;
  markerPath?: string;
  markerTimer?: NodeJS.Timeout;
  viewedTimer?: NodeJS.Timeout;
}

export interface TerminalLaunchResult {
  ok: boolean;
  fallback?: boolean;
  message?: string;
}

export class TerminalManager implements vscode.Disposable {
  private readonly instances = new Map<vscode.Terminal, ManagedTerminal>();
  private readonly streamedExecutions = new WeakSet<vscode.TerminalShellExecution>();
  private readonly changeEmitter = new vscode.EventEmitter<string>();
  private readonly disposables: vscode.Disposable[] = [];

  readonly onDidChangeSessionRuntime = this.changeEmitter.event;

  constructor(
    private readonly providerIcons: Readonly<Record<ProviderId, vscode.Uri>>,
    private readonly detection = new ProviderDetectionService()
  ) {
    this.disposables.push(
      this.changeEmitter,
      vscode.window.onDidCloseTerminal((terminal) => this.closeTerminal(terminal)),
      vscode.window.onDidChangeActiveTerminal((terminal) => this.viewTerminal(terminal)),
      vscode.window.onDidStartTerminalShellExecution((event) => {
        const instance = this.instances.get(event.terminal);
        if (!instance?.terminalOpen) return;
        if (
          !instance.execution &&
          event.execution.commandLine.value.trim() !== instance.expectedCommand.trim()
        ) return;
        instance.execution = event.execution;
        this.startOutputConsumption(instance, event.execution);
      }),
      vscode.window.onDidEndTerminalShellExecution((event) => {
        const instance = this.instances.get(event.terminal);
        if (instance?.execution === event.execution) this.markProcessEnded(instance);
      })
    );
  }

  runtime(sessionId: string): SessionRuntimeState {
    const instances = [...this.instances.values()].filter((instance) => instance.sessionId === sessionId && instance.terminalOpen);
    const taskState: TerminalTaskState = instances.some((instance) => instance.taskState === "working")
      ? "working"
      : instances.some((instance) => instance.taskState === "ready")
        ? "ready"
        : "idle";
    const latestWorking = instances
      .filter((instance) => instance.taskState === "working" && instance.liveText)
      .sort((left, right) => right.changedAt - left.changedAt)[0];
    return {
      terminalOpen: instances.length > 0,
      taskState,
      liveText: latestWorking?.liveText
    };
  }

  async launch(
    session: AgentSession,
    provider: CliProvider,
    projectPath: string,
    yolo: boolean
  ): Promise<TerminalLaunchResult> {
    if (!(await directoryExists(session.cwd))) {
      return { ok: false, message: `Working directory does not exist: ${session.cwd}` };
    }
    const detection = await this.detection.detect(provider);
    if (detection.status !== "available") return { ok: false, message: detection.reason };
    const template = yolo ? provider.yoloResumeCommandTemplate : provider.resumeCommandTemplate;
    if (!template) return { ok: false, message: `${yolo ? "YOLO resume" : "Resume"} command is not configured.` };
    const rendered = renderCommand(template, {
      provider: { ...provider, executable: detection.executablePath },
      session,
      projectPath,
      platform: process.platform
    });
    if (!rendered.ok) return { ok: false, message: `Missing command template variable: ${rendered.missingVariable}` };

    const token = randomUUID();
    const marker = process.platform === "win32" ? undefined : exitMarkerPath(token);
    if (marker) {
      await mkdir(path.dirname(marker), { recursive: true });
      await rm(marker, { force: true });
    }
    const launchCommand = marker ? wrapWithExitMarker(rendered.command, marker, process.platform) : rendered.command;

    let terminal: vscode.Terminal;
    try {
      terminal = vscode.window.createTerminal({
        name: session.name || `${provider.displayName} session`,
        cwd: session.cwd,
        iconPath: this.providerIcons[provider.id],
        isTransient: false
      });
    } catch {
      return this.clipboardFallback(rendered.command);
    }

    const instance: ManagedTerminal = {
      token,
      terminal,
      sessionId: session.id,
      sessionName: session.name,
      providerId: provider.id,
      expectedCommand: launchCommand,
      terminalOpen: true,
      taskState: "idle",
      changedAt: Date.now(),
      output: new TerminalOutputBuffer(),
      markerPath: marker,
      monitor: new LocalTerminalActivityMonitor(provider.id, session.historyFilePath, (event) => {
        const current = this.instances.get(terminal);
        if (!current?.terminalOpen) return;
        current.taskState = reduceTerminalTaskState(
          current.taskState,
          event === "started" ? "activity-started" : "activity-completed"
        );
        current.changedAt = Date.now();
        if (event === "completed") {
          current.liveText = undefined;
          this.scheduleViewedTransition(current);
        } else if (current.viewedTimer) {
          clearTimeout(current.viewedTimer);
          current.viewedTimer = undefined;
        }
        this.changeEmitter.fire(current.sessionId);
      })
    };
    this.instances.set(terminal, instance);
    await instance.monitor.start();
    if (marker) this.watchExitMarker(instance);
    terminal.show(false);
    this.changeEmitter.fire(session.id);

    try {
      const integration = await waitForShellIntegration(terminal, 2_500);
      if (!this.instances.has(terminal)) return { ok: false, message: "Terminal closed before the command started." };
      if (integration) {
        instance.execution = integration.executeCommand(launchCommand);
        this.startOutputConsumption(instance, instance.execution);
      } else {
        terminal.sendText(launchCommand, true);
      }
      return { ok: true };
    } catch {
      try {
        const retryIntegration = terminal.shellIntegration;
        if (retryIntegration) {
          instance.execution = retryIntegration.executeCommand(launchCommand);
          this.startOutputConsumption(instance, instance.execution);
        } else {
          terminal.sendText(launchCommand, true);
        }
        return { ok: true };
      } catch {
        this.markProcessEnded(instance);
        return this.clipboardFallback(rendered.command);
      }
    }
  }

  dispose(): void {
    for (const instance of this.instances.values()) this.cleanup(instance);
    this.instances.clear();
    for (const disposable of this.disposables.splice(0)) disposable.dispose();
  }

  private async consumeOutput(instance: ManagedTerminal, execution: vscode.TerminalShellExecution): Promise<void> {
    try {
      for await (const chunk of execution.read()) {
        if (!instance.terminalOpen) break;
        const text = instance.output.accept(chunk, instance.taskState === "working");
        if (!text || instance.taskState !== "working") continue;
        instance.liveText = text;
        instance.changedAt = Date.now();
        this.changeEmitter.fire(instance.sessionId);
      }
    } catch {
      // Output streaming is optional; lifecycle tracking continues through provider history.
    }
  }

  private startOutputConsumption(instance: ManagedTerminal, execution: vscode.TerminalShellExecution): void {
    if (this.streamedExecutions.has(execution)) return;
    this.streamedExecutions.add(execution);
    void this.consumeOutput(instance, execution);
  }

  private viewTerminal(terminal: vscode.Terminal | undefined): void {
    for (const instance of this.instances.values()) {
      if (instance.terminal !== terminal && instance.viewedTimer) {
        clearTimeout(instance.viewedTimer);
        instance.viewedTimer = undefined;
      }
    }
    if (!terminal) return;
    const instance = this.instances.get(terminal);
    if (!instance || instance.taskState !== "ready") return;
    this.scheduleViewedTransition(instance);
  }

  private closeTerminal(terminal: vscode.Terminal): void {
    const instance = this.instances.get(terminal);
    if (!instance) return;
    this.instances.delete(terminal);
    this.cleanup(instance);
    this.changeEmitter.fire(instance.sessionId);
  }

  private markProcessEnded(instance: ManagedTerminal): void {
    if (!instance.terminalOpen) return;
    instance.terminalOpen = false;
    instance.taskState = "idle";
    instance.liveText = undefined;
    instance.changedAt = Date.now();
    instance.monitor.stop();
    instance.output.reset();
    if (instance.markerTimer) clearInterval(instance.markerTimer);
    instance.markerTimer = undefined;
    if (instance.viewedTimer) clearTimeout(instance.viewedTimer);
    instance.viewedTimer = undefined;
    if (instance.markerPath) void rm(instance.markerPath, { force: true });
    this.changeEmitter.fire(instance.sessionId);
  }

  private cleanup(instance: ManagedTerminal): void {
    instance.monitor.stop();
    if (instance.markerTimer) clearInterval(instance.markerTimer);
    if (instance.viewedTimer) clearTimeout(instance.viewedTimer);
    if (instance.markerPath) void rm(instance.markerPath, { force: true });
  }

  private watchExitMarker(instance: ManagedTerminal): void {
    instance.markerTimer = setInterval(async () => {
      if (!instance.terminalOpen || !instance.markerPath) return;
      if (!(await fileFingerprint(instance.markerPath))) return;
      await readFile(instance.markerPath, "utf8").catch(() => "");
      this.markProcessEnded(instance);
    }, 500);
  }

  private scheduleViewedTransition(instance: ManagedTerminal): void {
    if (instance.taskState !== "ready" || vscode.window.activeTerminal !== instance.terminal) return;
    if (instance.viewedTimer) clearTimeout(instance.viewedTimer);
    instance.viewedTimer = setTimeout(() => {
      instance.viewedTimer = undefined;
      if (
        instance.terminalOpen &&
        instance.taskState === "ready" &&
        vscode.window.activeTerminal === instance.terminal
      ) {
        instance.taskState = reduceTerminalTaskState(instance.taskState, "viewed");
        instance.changedAt = Date.now();
        this.changeEmitter.fire(instance.sessionId);
      }
    }, 500);
  }

  private async clipboardFallback(command: string): Promise<TerminalLaunchResult> {
    await vscode.env.clipboard.writeText(command);
    await vscode.commands.executeCommand("workbench.action.terminal.new");
    return { ok: true, fallback: true, message: "The command was copied to the clipboard." };
  }
}

async function waitForShellIntegration(
  terminal: vscode.Terminal,
  timeoutMillis: number
): Promise<vscode.TerminalShellIntegration | undefined> {
  if (terminal.shellIntegration) return terminal.shellIntegration;
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value?: vscode.TerminalShellIntegration): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      listener.dispose();
      resolve(value);
    };
    const listener = vscode.window.onDidChangeTerminalShellIntegration((event) => {
      if (event.terminal === terminal) finish(event.shellIntegration);
    });
    const timer = setTimeout(() => finish(), timeoutMillis);
  });
}
