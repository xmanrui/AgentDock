import type { CliProvider, ProviderId } from "./model.js";

export const DEFAULT_PROVIDERS: readonly CliProvider[] = [
  {
    id: "codex",
    configKey: "codex",
    displayName: "Codex",
    executable: "codex",
    detectCommand: "codex --version",
    startCommandTemplate: "{{executable}}",
    resumeCommandTemplate: "{{executable}} resume {{providerSessionId?}}",
    yoloResumeCommandTemplate:
      "{{executable}} resume --dangerously-bypass-approvals-and-sandbox {{providerSessionId?}}",
    enabled: true
  },
  {
    id: "claude-code",
    configKey: "claudeCode",
    displayName: "Claude Code",
    executable: "claude",
    detectCommand: "claude --version",
    startCommandTemplate: "{{executable}} --ide --name {{sessionName}}",
    resumeCommandTemplate: "{{executable}} --resume {{providerSessionId?}} --ide",
    yoloResumeCommandTemplate:
      "{{executable}} --resume {{providerSessionId?}} --ide --dangerously-skip-permissions",
    enabled: true
  },
  {
    id: "gemini",
    configKey: "gemini",
    displayName: "Gemini CLI",
    executable: "gemini",
    detectCommand: "gemini --version",
    startCommandTemplate: "{{executable}}",
    resumeCommandTemplate: "{{executable}} --resume {{providerSessionId?}}",
    yoloResumeCommandTemplate: "{{executable}} --resume {{providerSessionId?}} --yolo",
    enabled: true
  }
] as const;

export function providerById(id: ProviderId, providers: readonly CliProvider[] = DEFAULT_PROVIDERS): CliProvider {
  const provider = providers.find((candidate) => candidate.id === id);
  if (!provider) {
    throw new Error(`Unsupported provider: ${id}`);
  }
  return provider;
}
