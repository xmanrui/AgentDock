import { describe, expect, it } from "vitest";
import { BoundedUtf8LineDecoder, parseTerminalActivityEvent } from "../src/terminal/activity.js";

describe("parseTerminalActivityEvent", () => {
  it("parses Codex task lifecycle records", () => {
    expect(parseTerminalActivityEvent("codex", JSON.stringify({ type: "event_msg", payload: { type: "task_started" } }))).toBe("started");
    expect(parseTerminalActivityEvent("codex", JSON.stringify({ type: "event_msg", payload: { type: "task_complete" } }))).toBe("completed");
    expect(parseTerminalActivityEvent("codex", JSON.stringify({ type: "event_msg", payload: { type: "turn_aborted" } }))).toBe("completed");
    expect(parseTerminalActivityEvent("codex", JSON.stringify({ type: "response_item", payload: { type: "message" } }))).toBeUndefined();
  });

  it("parses Claude prompts and turn completion while ignoring metadata and tools", () => {
    expect(parseTerminalActivityEvent("claude-code", JSON.stringify({
      type: "user",
      message: { role: "user", content: "Implement the feature" }
    }))).toBe("started");
    expect(parseTerminalActivityEvent("claude-code", JSON.stringify({
      type: "system",
      subtype: "turn_duration",
      durationMs: 1_200
    }))).toBe("completed");
    expect(parseTerminalActivityEvent("claude-code", JSON.stringify({
      type: "user",
      message: { role: "user", content: [{ type: "tool_result", content: "done" }] }
    }))).toBeUndefined();
    expect(parseTerminalActivityEvent("claude-code", JSON.stringify({
      type: "user",
      isMeta: true,
      message: { role: "user", content: "metadata" }
    }))).toBeUndefined();
  });
});

describe("BoundedUtf8LineDecoder", () => {
  it("decodes split UTF-8 lines", () => {
    const decoder = new BoundedUtf8LineDecoder(1_024);
    const bytes = Buffer.from("第一行\n第二行\n", "utf8");
    expect(decoder.accept(bytes.subarray(0, 5))).toEqual([]);
    expect(decoder.accept(bytes.subarray(5))).toEqual(["第一行", "第二行"]);
  });

  it("skips oversized records without blocking later lifecycle lines", () => {
    const decoder = new BoundedUtf8LineDecoder(16);
    expect(decoder.accept(Buffer.from(`${"x".repeat(40)}\nshort\n`))).toEqual(["short"]);
  });
});
