import { mkdtemp, rm, writeFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import type { AgentSession, ProviderId } from "../src/core/model.js";
import { SessionContentService } from "../src/services/sessionContent.js";
import { SessionMetricsService } from "../src/services/sessionMetrics.js";

const temporaryDirectories: string[] = [];
const NOW = Date.parse("2026-07-11T12:00:00Z");

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe("SessionMetricsService", () => {
  it("computes Codex token deltas and completed-task response averages", async () => {
    const source = await history("codex.jsonl", [
      "not-json",
      codexUsage("2026-07-01T10:00:00Z", 100, 100),
      codexUsage("2026-07-02T10:00:00Z", 150, 50),
      codexUsage("2026-07-02T10:00:01Z", 150, 50),
      codexTask("2026-07-05T10:00:01Z", "task_complete", 1_000),
      codexTask("2026-07-10T09:00:02Z", "task_complete", 2_000),
      codexUsage("2026-07-10T10:00:00Z", 30, 30),
      codexTask("2026-07-10T10:30:00Z", "turn_aborted", 90_000),
      codexTask("2026-07-10T11:00:04Z", "task_complete", 4_000)
    ].join("\n"));
    const target = session("codex", source);
    const service = metricsService(path.dirname(source));

    const metrics = await service.load(target);

    expect(metrics.totalTokens).toBe(180);
    expect(metrics.dailyTokens).toEqual([0, 0, 0, 0, 0, 30, 0]);
    expect(metrics.dailyAverageResponseMillis).toEqual([1_000, null, null, null, null, 3_000, null]);
    expect(service.cached(target)).toEqual(metrics);
  });

  it("deduplicates Claude usage and includes cache token fields", async () => {
    const source = await history("claude.jsonl", [
      claudeUsage("2026-06-20T10:00:00Z", "old", 100, 20, 0, 0),
      claudeUsage("2026-07-05T10:00:00Z", "message-a", 2, 10, 20, 30),
      claudeUsage("2026-07-05T10:00:01Z", "message-a", 2, 10, 20, 30),
      claudeDuration("2026-07-05T10:00:02Z", 2_000),
      claudeUsage("2026-07-06T10:00:00Z", "message-a", 2, 15, 20, 30),
      claudeDuration("2026-07-06T10:00:01Z", 5_000),
      claudeUsage("2026-07-10T10:00:00Z", "message-b", 3, 4, 5, 6),
      claudeDuration("2026-07-10T10:00:01Z", 4_000)
    ].join("\n"));

    const metrics = await metricsService(path.dirname(source)).load(session("claude-code", source));

    expect(metrics.totalTokens).toBe(205);
    expect(metrics.dailyTokens).toEqual([0, 67, 0, 0, 0, 18, 0]);
    expect(metrics.dailyAverageResponseMillis).toEqual([2_000, 5_000, null, null, null, 4_000, null]);
  });

  it("computes Gemini tokens and user-to-model response durations", async () => {
    const source = await history("gemini.json", JSON.stringify({
      sessionId: "gemini-session",
      messages: [
        { type: "user", timestamp: "2026-07-10T10:00:00Z", content: "First" },
        { type: "gemini", timestamp: "2026-07-10T10:00:02Z", content: "Reply", tokens: { total: 30 } },
        { type: "user", timestamp: "2026-07-10T11:00:00Z", content: "Second" },
        { type: "gemini", timestamp: "2026-07-10T11:00:04Z", content: "Reply", tokens: { total: 40 } }
      ]
    }));

    const metrics = await metricsService(path.dirname(source)).load(session("gemini", source));

    expect(metrics.totalTokens).toBe(70);
    expect(metrics.dailyTokens).toEqual([0, 0, 0, 0, 0, 70, 0]);
    expect(metrics.dailyAverageResponseMillis).toEqual([null, null, null, null, null, 3_000, null]);
  });

  it("aggregates only cached totals from the requested provider", async () => {
    const first = session("codex", await history("first.jsonl", codexUsage("2026-07-10T10:00:00Z", 90, 90)));
    const second = session("codex", await history("second.jsonl", codexUsage("2026-07-10T11:00:00Z", 120, 120)));
    const claude = session("claude-code", await history("claude.jsonl", claudeUsage("2026-07-10T12:00:00Z", "m", 3, 7, 0, 0)));
    const service = metricsService(path.dirname(first.historyFilePath));
    await Promise.all([service.load(first), service.load(second), service.load(claude)]);

    expect(service.cachedProviderTotal([first, second, claude], "codex")).toBe(210);
    expect(service.cachedProviderTotal([first, second, claude], "claude-code")).toBe(10);
    expect(service.cachedProviderTotal([first, second, claude], "gemini")).toBeNull();
  });
});

function metricsService(home: string): SessionMetricsService {
  return new SessionMetricsService(new SessionContentService(home), () => NOW, "UTC");
}

function session(providerId: ProviderId, historyFilePath: string): AgentSession {
  return {
    id: `${providerId}:${path.basename(historyFilePath)}`,
    projectPath: path.dirname(historyFilePath),
    name: "Session",
    providerId,
    cwd: path.dirname(historyFilePath),
    providerSessionId: "test",
    historyFilePath,
    summary: "",
    createdAt: 1,
    updatedAt: 2,
    pinned: false
  };
}

async function history(name: string, content: string): Promise<string> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "agentdock-metrics-"));
  temporaryDirectories.push(directory);
  const source = path.join(directory, name);
  await writeFile(source, content);
  return source;
}

function codexUsage(timestamp: string, cumulative: number, last: number): string {
  return JSON.stringify({
    timestamp,
    type: "event_msg",
    payload: {
      type: "token_count",
      info: { last_token_usage: { total_tokens: last }, total_token_usage: { total_tokens: cumulative } }
    }
  });
}

function codexTask(timestamp: string, type: string, duration: number): string {
  return JSON.stringify({ timestamp, type: "event_msg", payload: { type, duration_ms: duration } });
}

function claudeUsage(timestamp: string, id: string, input: number, output: number, creation: number, read: number): string {
  return JSON.stringify({
    timestamp,
    type: "assistant",
    message: {
      id,
      usage: {
        input_tokens: input,
        output_tokens: output,
        cache_creation_input_tokens: creation,
        cache_read_input_tokens: read
      }
    }
  });
}

function claudeDuration(timestamp: string, durationMs: number): string {
  return JSON.stringify({ timestamp, type: "system", subtype: "turn_duration", durationMs });
}
