import type { TerminalTaskState } from "../core/model.js";

export type TerminalTaskEvent = "activity-started" | "activity-completed" | "viewed";

export function reduceTerminalTaskState(state: TerminalTaskState, event: TerminalTaskEvent): TerminalTaskState {
  if (event === "activity-started") return "working";
  if (event === "activity-completed") return "ready";
  return state === "ready" ? "idle" : state;
}
