import assert from "node:assert/strict";
import { chmod, rm, writeFile } from "node:fs/promises";
import * as path from "node:path";
import * as vscode from "vscode";
import { providerById } from "../../src/core/providers.js";
import type { AgentSession, ProviderId } from "../../src/core/model.js";
import { TerminalManager } from "../../src/terminal/terminalManager.js";

export async function run(): Promise<void> {
  const extension = vscode.extensions.getExtension("xmanrui.agentdock");
  assert.ok(extension, "AgentDock extension should be installed in the test host");
  await extension.activate();
  assert.equal(extension.isActive, true, "AgentDock extension should activate");

  const commands = await vscode.commands.getCommands(true);
  assert.ok(commands.includes("agentdock.refreshSessions"), "refresh command should be registered");
  assert.ok(commands.includes("agentdock.openSettings"), "settings command should be registered");

  await vscode.commands.executeCommand("agentdock.refreshSessions");
  const configuration = vscode.workspace.getConfiguration("agentdock");
  assert.equal(configuration.get("refreshIntervalMinutes"), 3);
  assert.equal(configuration.get("previewMessageLimit"), 9);

  const providers = vscode.workspace.getConfiguration("agentdock.providers");
  assert.equal(providers.get("codex.yoloResumeCommand"), "{{executable}} resume --dangerously-bypass-approvals-and-sandbox {{providerSessionId?}}");
  assert.equal(providers.get("claudeCode.yoloResumeCommand"), "{{executable}} --resume {{providerSessionId?}} --ide --dangerously-skip-permissions");
  assert.equal(providers.get("gemini.yoloResumeCommand"), "{{executable}} --resume {{providerSessionId?}} --yolo");

  if (process.platform !== "win32") await verifyTerminalLifecycle(extension.extensionUri);
}

async function verifyTerminalLifecycle(extensionUri: vscode.Uri): Promise<void> {
  const workspacePath = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
  assert.ok(workspacePath, "integration workspace should be open");
  const executable = path.join(workspacePath, "fake-agentdock-cli.sh");
  const history = path.join(workspacePath, "fake-agentdock-history.jsonl");
  const startOutput = path.join(workspacePath, "fake-agentdock-start-output");
  const finish = path.join(workspacePath, "fake-agentdock-finish");
  await Promise.all([history, startOutput, finish].map((file) => rm(file, { force: true })));
  await writeFile(history, "");
  await writeFile(executable, `#!/bin/sh
if [ "$1" = "--version" ]; then
  printf '%s\\n' 'fake-agentdock 1.0'
  exit 0
fi
while [ ! -f '${shellLiteral(startOutput)}' ]; do sleep 0.1; done
printf '%s\\n' '• Streaming integration output is visible.'
sleep 0.2
printf '%s\\n' '• Streaming integration output continues.'
while [ ! -f '${shellLiteral(finish)}' ]; do sleep 0.1; done
exit 0
`);
  await chmod(executable, 0o755);

  const icon = vscode.Uri.joinPath(extensionUri, "media", "codex.svg");
  const icons: Record<ProviderId, vscode.Uri> = { codex: icon, "claude-code": icon, gemini: icon };
  const manager = new TerminalManager(icons);
  const session: AgentSession = {
    id: "codex:integration",
    projectPath: workspacePath,
    name: "AgentDock integration terminal",
    providerId: "codex",
    cwd: workspacePath,
    providerSessionId: "integration",
    historyFilePath: history,
    summary: "",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    pinned: false
  };

  try {
    const launch = await manager.launch(session, { ...providerById("codex"), executable }, workspacePath, false);
    assert.equal(launch.ok, true, launch.message);
    assert.equal(manager.runtime(session.id).terminalOpen, true);

    await writeFile(history, `${JSON.stringify({ type: "event_msg", payload: { type: "task_started" } })}\n`, { flag: "a" });
    await waitUntil(() => manager.runtime(session.id).taskState === "working", 5_000, "terminal should enter Working");
    await writeFile(startOutput, "go");

    const terminal = vscode.window.terminals.find((candidate) => candidate.name === session.name);
    if (terminal?.shellIntegration) {
      try {
        await waitUntil(() => Boolean(manager.runtime(session.id).liveText), 5_000, "shell output should reach the live ticker");
      } catch {
        assert.fail(`shell output should reach the live ticker; runtime=${JSON.stringify(manager.runtime(session.id))}`);
      }
    }

    await writeFile(history, `${JSON.stringify({ type: "event_msg", payload: { type: "task_complete" } })}\n`, { flag: "a" });
    await waitUntil(() => manager.runtime(session.id).taskState === "ready", 5_000, "terminal should become Ready");
    await writeFile(finish, "done");
    await waitUntil(() => !manager.runtime(session.id).terminalOpen, 5_000, "CLI exit should clear terminal-open state");
    assert.equal(manager.runtime(session.id).taskState, "idle");
    terminal?.dispose();
  } finally {
    manager.dispose();
    await Promise.all([executable, history, startOutput, finish].map((file) => rm(file, { force: true })));
  }
}

async function waitUntil(predicate: () => boolean, timeoutMillis: number, message: string): Promise<void> {
  const deadline = Date.now() + timeoutMillis;
  while (Date.now() < deadline) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert.fail(message);
}

function shellLiteral(value: string): string {
  return value.replace(/'/g, `'"'"'`);
}
