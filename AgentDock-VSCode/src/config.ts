import * as vscode from "vscode";
import { DEFAULT_PROVIDERS } from "./core/providers.js";
import type { CliProvider } from "./core/model.js";

export function configuredProviders(): CliProvider[] {
  const configuration = vscode.workspace.getConfiguration("agentdock.providers");
  return DEFAULT_PROVIDERS.map((provider) => {
    const prefix = provider.configKey;
    return {
      ...provider,
      enabled: configuration.get<boolean>(`${prefix}.enabled`, provider.enabled),
      executable: configuration.get<string>(`${prefix}.executable`, provider.executable).trim(),
      detectCommand: configuration.get<string>(`${prefix}.detectCommand`, provider.detectCommand).trim(),
      startCommandTemplate: configuration
        .get<string>(`${prefix}.startCommand`, provider.startCommandTemplate)
        .trim(),
      resumeCommandTemplate: configuration
        .get<string>(`${prefix}.resumeCommand`, provider.resumeCommandTemplate)
        .trim(),
      yoloResumeCommandTemplate: configuration
        .get<string>(`${prefix}.yoloResumeCommand`, provider.yoloResumeCommandTemplate)
        .trim()
    };
  });
}

export function refreshIntervalMillis(): number {
  const minutes = vscode.workspace.getConfiguration("agentdock").get<number>("refreshIntervalMinutes", 3);
  return Math.max(1, Math.min(60, minutes)) * 60_000;
}

export function previewMessageLimit(): number {
  const limit = vscode.workspace.getConfiguration("agentdock").get<number>("previewMessageLimit", 9);
  return Math.max(1, Math.min(100, Math.floor(limit)));
}
