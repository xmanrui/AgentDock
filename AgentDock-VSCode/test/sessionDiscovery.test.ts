import { createHash } from "node:crypto";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { DEFAULT_PROVIDERS } from "../src/core/providers.js";
import { SessionDiscoveryService } from "../src/services/sessionDiscovery.js";

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe("SessionDiscoveryService", () => {
  it("discovers a project-scoped Codex session and uses its index title", async () => {
    const fixture = await createFixture();
    const id = "019f4295-c4d2-7131-8775-706769a5b630";
    await fixture.codexIndex(id, "更新 AgentDock 插件", "2026-07-08T16:40:00Z");
    await fixture.codex(id, [
      { timestamp: "2026-07-08T16:36:48Z", type: "session_meta", payload: { id, cwd: fixture.project } },
      { timestamp: "2026-07-08T16:36:49Z", type: "response_item", payload: { type: "message", role: "user", content: [{ type: "input_text", text: "请更新 AgentDock 插件" }] } }
    ]);

    const sessions = await fixture.discovery.discover(fixture.project, DEFAULT_PROVIDERS);

    expect(sessions).toHaveLength(1);
    expect(sessions[0]).toMatchObject({ id: `codex:${id}`, name: "更新 AgentDock 插件", providerId: "codex", cwd: fixture.project });
  });

  it("ignores Codex histories from another working directory", async () => {
    const fixture = await createFixture();
    const id = "019f3dc4-caa4-7040-840a-1490c1188bc3";
    const otherProject = path.join(fixture.root, "other");
    await mkdir(otherProject);
    await fixture.codex(id, [
      { timestamp: "2026-07-07T18:09:00Z", type: "session_meta", payload: { id, cwd: otherProject } },
      { timestamp: "2026-07-07T18:09:01Z", type: "response_item", payload: { type: "message", role: "user", content: "AgentDock" } }
    ]);

    expect(await fixture.discovery.discover(fixture.project, DEFAULT_PROVIDERS)).toEqual([]);
  });

  it("discovers current Claude transcript content and strips noisy context", async () => {
    const fixture = await createFixture();
    const id = "93a8b2df-131c-48de-a4ea-3bf164ebee5f";
    await fixture.claude(id, [
      { type: "mode", mode: "normal", sessionId: id },
      {
        type: "user",
        message: { role: "user", content: `Context: local shell\ncwd: ${fixture.project}\nshell: zsh\nMake AgentDock match the prototype. Keep the card readable.` },
        timestamp: "2026-07-08T19:20:07Z",
        cwd: fixture.project,
        sessionId: id
      }
    ]);

    const session = (await fixture.discovery.discover(fixture.project, DEFAULT_PROVIDERS))[0];

    expect(session).toMatchObject({
      id: `claude-code:${id}`,
      name: "Make AgentDock match the prototype.",
      summary: "Make AgentDock match the prototype.",
      providerId: "claude-code"
    });
  });

  it("supports Claude project directories that sanitize Windows drive colons", async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), "agentdock-claude-windows-"));
    temporaryDirectories.push(root);
    const home = path.join(root, "home");
    const project = path.join(root, "C:", "AgentDock");
    const id = "claude-windows-session";
    await Promise.all([mkdir(home), mkdir(project, { recursive: true })]);
    const directory = project.replace(/[:\\/]/g, "-");
    const history = path.join(home, ".claude", "projects", directory, `${id}.jsonl`);
    await mkdir(path.dirname(history), { recursive: true });
    await writeFile(history, JSON.stringify({
      type: "user",
      sessionId: id,
      cwd: project,
      timestamp: "2026-07-08T19:20:45Z",
      message: { role: "user", content: "Verify Windows history discovery." }
    }));

    const sessions = await new SessionDiscoveryService(home).discover(project, DEFAULT_PROVIDERS);
    expect(sessions[0]).toMatchObject({ id: `claude-code:${id}`, name: "Verify Windows history discovery." });
  });

  it("skips context-only Codex messages and selects the first real prompt", async () => {
    const fixture = await createFixture();
    const id = "019f7000-c4d2-7131-8775-706769a5b630";
    await fixture.codexIndex(id, "", "2026-07-09T03:25:03Z");
    await fixture.codex(id, [
      { timestamp: "2026-07-09T03:25:00Z", type: "session_meta", payload: { id, cwd: fixture.project } },
      { timestamp: "2026-07-09T03:25:01Z", type: "response_item", payload: { type: "message", role: "user", content: `<environment_context>\n<cwd>${fixture.project}</cwd>\n<shell>zsh</shell>\n</environment_context>` } },
      { timestamp: "2026-07-09T03:25:02Z", type: "response_item", payload: { type: "message", role: "user", content: "Show the current model id. Then stop." } }
    ]);

    const session = (await fixture.discovery.discover(fixture.project, DEFAULT_PROVIDERS))[0];
    expect(session?.name).toBe("Show the current model id.");
    expect(session?.summary).toBe("Show the current model id.");
  });

  it("discovers Gemini chats only for the matching project hash", async () => {
    const fixture = await createFixture();
    const id = "b77d543d-709c-40ba-b8da-7bb5b0f6767b";
    await fixture.gemini(id, {
      sessionId: id,
      projectHash: fixture.projectHash,
      startTime: "2026-07-11T15:13:05Z",
      lastUpdated: "2026-07-11T15:14:48Z",
      messages: [
        { type: "user", content: "Add Gemini CLI support." },
        { type: "gemini", content: "I will inspect the provider architecture." }
      ]
    });

    const session = (await fixture.discovery.discover(fixture.project, DEFAULT_PROVIDERS))[0];
    expect(session).toMatchObject({
      id: `gemini:${id}`,
      providerId: "gemini",
      name: "Add Gemini CLI support.",
      summary: "Add Gemini CLI support.",
      cwd: fixture.project
    });
  });
});

async function createFixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "agentdock-discovery-"));
  temporaryDirectories.push(root);
  const home = path.join(root, "home");
  const project = path.join(root, "AgentDock");
  await Promise.all([mkdir(home), mkdir(project)]);
  const projectHash = createHash("sha256").update(project).digest("hex");
  return {
    root,
    home,
    project,
    projectHash,
    discovery: new SessionDiscoveryService(home),
    async codexIndex(id: string, title: string, updatedAt: string) {
      const file = path.join(home, ".codex", "session_index.jsonl");
      await mkdir(path.dirname(file), { recursive: true });
      await writeFile(file, `${JSON.stringify({ id, thread_name: title, updated_at: updatedAt })}\n`, { flag: "a" });
    },
    async codex(id: string, records: unknown[]) {
      const file = path.join(home, ".codex", "sessions", "2026", "07", "09", `rollout-2026-07-09T00-35-39-${id}.jsonl`);
      await mkdir(path.dirname(file), { recursive: true });
      await writeFile(file, `${records.map((record) => JSON.stringify(record)).join("\n")}\n`);
    },
    async claude(id: string, records: unknown[]) {
      const directory = project.replace(/[\\/]/g, "-");
      const file = path.join(home, ".claude", "projects", directory, `${id}.jsonl`);
      await mkdir(path.dirname(file), { recursive: true });
      await writeFile(file, `${records.map((record) => JSON.stringify(record)).join("\n")}\n`);
    },
    async gemini(id: string, record: unknown) {
      const file = path.join(home, ".gemini", "tmp", projectHash, "chats", `session-${id.slice(0, 8)}.json`);
      await mkdir(path.dirname(file), { recursive: true });
      await writeFile(file, JSON.stringify(record));
    }
  };
}
