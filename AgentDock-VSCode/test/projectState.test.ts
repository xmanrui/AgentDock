import { describe, expect, it } from "vitest";
import type { AgentSession, ProviderId, SessionMetrics } from "../src/core/model.js";
import { ProjectStateStore, type MementoLike } from "../src/services/projectState.js";

class MemoryMemento implements MementoLike {
  readonly values = new Map<string, unknown>();

  get<T>(key: string, defaultValue: T): T {
    return (this.values.has(key) ? this.values.get(key) : defaultValue) as T;
  }

  update(key: string, value: unknown): Thenable<void> {
    this.values.set(key, structuredClone(value));
    return Promise.resolve();
  }
}

describe("ProjectStateStore", () => {
  it("merges discoveries while preserving pins and sorting pinned sessions first", async () => {
    const memento = new MemoryMemento();
    const store = new ProjectStateStore(memento);
    await store.mergeDiscovered("/project", [session("codex", "a", 100), session("gemini", "b", 200)]);
    await store.togglePin("codex:a");

    const sessions = await store.mergeDiscovered("/project", [
      { ...session("codex", "a", 300), name: "Updated title" },
      session("gemini", "b", 400)
    ]);

    expect(sessions.map((item) => item.id)).toEqual(["codex:a", "gemini:b"]);
    expect(sessions[0]?.pinned).toBe(true);
    expect(sessions[0]?.name).toBe("Updated title");
  });

  it("persists and restores validated metrics", async () => {
    const memento = new MemoryMemento();
    const store = new ProjectStateStore(memento);
    await store.mergeDiscovered("/project", [session("codex", "a", 100)]);
    const metrics: SessionMetrics = {
      totalTokens: 120,
      dailyTokens: [0, 0, 0, 0, 0, 100, 20],
      dailyAverageResponseMillis: [null, null, null, null, null, 2_000, 3_000]
    };
    await store.saveMetrics("codex:a", {
      metrics,
      sourceModifiedAt: 10,
      sourceLength: 20,
      anchorDate: "2026-07-12"
    });

    expect(new ProjectStateStore(memento).metrics("codex:a")?.metrics).toEqual(metrics);
  });

  it("ignores malformed persisted state", () => {
    const memento = new MemoryMemento();
    memento.values.set("agentdock.projectState", { version: 1, sessions: { broken: { session: { id: "broken" } } } });
    expect(new ProjectStateStore(memento).sessions("/project")).toEqual([]);
  });
});

function session(providerId: ProviderId, id: string, updatedAt: number): AgentSession {
  return {
    id: `${providerId}:${id}`,
    projectPath: "/project",
    name: `Session ${id}`,
    providerId,
    cwd: "/project",
    providerSessionId: id,
    historyFilePath: `/history/${id}`,
    summary: "Summary",
    createdAt: updatedAt - 10,
    updatedAt,
    pinned: false
  };
}
