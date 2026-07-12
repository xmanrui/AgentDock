import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import type { AgentSession, ProviderId } from "../src/core/model.js";
import { SessionContentService } from "../src/services/sessionContent.js";

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe("SessionContentService", () => {
  it("loads Codex messages while removing injected context and non-message items", async () => {
    const root = await temporaryDirectory();
    const source = path.join(root, "codex.jsonl");
    await writeFile(source, [
      JSON.stringify({ type: "session_meta", payload: { id: "session" } }),
      JSON.stringify({
        type: "response_item",
        payload: {
          type: "message",
          role: "user",
          content: [{ type: "input_text", text: "Build a hover preview.\n<environment_context>secret</environment_context>\nKeep this line." }]
        }
      }),
      JSON.stringify({ type: "response_item", payload: { type: "function_call", name: "shell" } }),
      JSON.stringify({
        type: "response_item",
        payload: { type: "message", role: "assistant", content: [{ type: "output_text", text: "Implemented." }] }
      })
    ].join("\n"));

    const preview = await new SessionContentService(root).load(session("codex", source));

    expect(preview).toEqual({
      messages: [
        { role: "user", text: "Build a hover preview.\n\nKeep this line." },
        { role: "assistant", text: "Implemented." }
      ],
      omittedMessageCount: 0
    });
  });

  it("loads Claude text blocks and ignores metadata, thinking, and tool results", async () => {
    const root = await temporaryDirectory();
    const source = path.join(root, "claude.jsonl");
    await writeFile(source, [
      JSON.stringify({ type: "user", isMeta: true, message: { role: "user", content: "hidden" } }),
      JSON.stringify({ type: "user", message: { role: "user", content: "Explain this project." } }),
      JSON.stringify({
        type: "assistant",
        message: {
          role: "assistant",
          content: [
            { type: "thinking", thinking: "private" },
            { type: "text", text: "It manages local sessions." },
            { type: "tool_use", name: "Read" }
          ]
        }
      }),
      JSON.stringify({ type: "user", message: { role: "user", content: [{ type: "tool_result", content: "tool output" }] } })
    ].join("\n"));

    const preview = await new SessionContentService(root).load(session("claude-code", source));

    expect(preview.messages.map((message) => message.text)).toEqual([
      "Explain this project.",
      "It manages local sessions."
    ]);
  });

  it("keeps the opening message and the nine most recent unique messages", async () => {
    const root = await temporaryDirectory();
    const source = path.join(root, "codex.jsonl");
    const messages = Array.from({ length: 14 }, (_, offset) => {
      const index = offset + 1;
      return JSON.stringify({
        type: "response_item",
        payload: {
          type: "message",
          role: index % 2 === 0 ? "assistant" : "user",
          content: [{ type: "text", text: `message ${index}` }]
        }
      });
    });
    await writeFile(source, messages.join("\n"));

    const preview = await new SessionContentService(root).load(session("codex", source));

    expect(preview.messages.map((message) => message.text)).toEqual([
      "message 1",
      ...Array.from({ length: 9 }, (_, index) => `message ${index + 6}`)
    ]);
    expect(preview.omittedMessageCount).toBe(4);
  });

  it("applies preview limit changes without restarting the service", async () => {
    const root = await temporaryDirectory();
    const source = path.join(root, "codex.jsonl");
    await writeFile(source, [1, 2, 3, 4].map((index) => JSON.stringify({
      type: "response_item",
      payload: { type: "message", role: index % 2 ? "user" : "assistant", content: `message ${index}` }
    })).join("\n"));
    const service = new SessionContentService(root, 1);

    expect((await service.load(session("codex", source))).messages.map((message) => message.text)).toEqual(["message 1", "message 4"]);
    service.setRecentMessageLimit(2);
    expect((await service.load(session("codex", source))).messages.map((message) => message.text)).toEqual(["message 1", "message 3", "message 4"]);
  });

  it("loads Gemini user and model messages", async () => {
    const root = await temporaryDirectory();
    const source = path.join(root, "gemini.json");
    await writeFile(source, JSON.stringify({
      sessionId: "gemini-session",
      messages: [
        { type: "info", content: "metadata" },
        { type: "user", content: "Explain this module." },
        { type: "gemini", content: "It manages sessions." },
        { type: "error", content: "transient" }
      ]
    }));

    const preview = await new SessionContentService(root).load(session("gemini", source));

    expect(preview.messages).toEqual([
      { role: "user", text: "Explain this module." },
      { role: "assistant", text: "It manages sessions." }
    ]);
  });

  it("falls back to the stored summary when history is unavailable", async () => {
    const root = await temporaryDirectory();
    const preview = await new SessionContentService(root).load({
      ...session("codex", path.join(root, "missing.jsonl")),
      summary: "Fallback purpose."
    });

    expect(preview.messages).toEqual([{ role: "user", text: "Fallback purpose." }]);
    expect(preview.notice).toContain("not available");
  });
});

function session(providerId: ProviderId, historyFilePath: string): AgentSession {
  return {
    id: `${providerId}:session`,
    projectPath: path.dirname(historyFilePath),
    name: "Session title",
    providerId,
    cwd: path.dirname(historyFilePath),
    providerSessionId: "session",
    historyFilePath,
    summary: "",
    createdAt: 1,
    updatedAt: 2,
    pinned: false
  };
}

async function temporaryDirectory(): Promise<string> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "agentdock-content-"));
  temporaryDirectories.push(directory);
  await mkdir(directory, { recursive: true });
  return directory;
}
