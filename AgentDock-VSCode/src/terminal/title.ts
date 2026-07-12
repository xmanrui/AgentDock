import type { TerminalTaskState } from "../core/model.js";

export function terminalTabTitle(baseTitle: string, taskState: TerminalTaskState): string {
  if (taskState === "working") return `$(loading~spin) ${baseTitle}`;
  if (taskState === "ready") return `\u{1F7E2} ${baseTitle}`;
  return baseTitle;
}
