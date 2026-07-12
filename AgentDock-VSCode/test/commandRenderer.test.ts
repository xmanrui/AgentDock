import { describe, expect, it } from "vitest";
import { renderCommand } from "../src/core/commandRenderer.js";
import { providerById } from "../src/core/providers.js";
import type { AgentSession, ProviderCommandContext } from "../src/core/model.js";

const session: AgentSession = {
  id: "codex:session-id",
  projectPath: "/tmp/project with spaces",
  name: "Session  with  intentional spacing",
  providerId: "codex",
  cwd: "/tmp/project with spaces",
  providerSessionId: "session-id",
  historyFilePath: "/tmp/session.jsonl",
  summary: "Summary",
  createdAt: 1,
  updatedAt: 2,
  pinned: false
};

function context(platform: NodeJS.Platform = "darwin"): ProviderCommandContext {
  return {
    provider: providerById("codex"),
    session,
    projectPath: session.projectPath,
    platform
  };
}

describe("renderCommand", () => {
  it("quotes substituted values without collapsing their internal whitespace", () => {
    expect(renderCommand("{{executable}} --name {{sessionName}}", context())).toEqual({
      ok: true,
      command: "codex --name 'Session  with  intentional spacing'"
    });
  });

  it("removes a missing optional value", () => {
    const result = renderCommand("{{executable}} resume {{providerSessionId?}}", {
      ...context(),
      session: { ...session, providerSessionId: "" }
    });
    expect(result).toEqual({ ok: true, command: "codex resume" });
  });

  it("reports a missing required value", () => {
    const result = renderCommand("{{executable}} --cwd {{cwd}}", {
      ...context(),
      session: { ...session, cwd: "" }
    });
    expect(result).toEqual({ ok: false, missingVariable: "cwd" });
  });

  it("uses Windows-compatible quoting", () => {
    expect(renderCommand("{{executable}} --cwd {{cwd}}", context("win32"))).toEqual({
      ok: true,
      command: 'codex --cwd "/tmp/project with spaces"'
    });
  });
});
