import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { arrayValue, asRecord, epochMillis, objectValue, parseJsonObject, stringValue, textFromContent } from "../core/json.js";
import { belongsToProject, directoryExists, fileFingerprint, readLines, walkFiles } from "../core/files.js";
import { sessionSummary, sessionTitle } from "../core/text.js";
import type { AgentSession, CliProvider } from "../core/model.js";
import type { JsonRecord } from "../core/json.js";

interface CacheEntry {
  fingerprint: string;
  session?: AgentSession;
}

interface CodexIndexEntry {
  threadName?: string;
  updatedAt?: number;
}

const UUID_PATTERN = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/;

export class SessionDiscoveryService {
  private readonly cache = new Map<string, CacheEntry>();
  private codexIndexCache?: { fingerprint: string; entries: Map<string, CodexIndexEntry> };

  constructor(private readonly homeDirectory = os.homedir()) {}

  async discover(projectPath: string, providers: readonly CliProvider[]): Promise<AgentSession[]> {
    const normalizedProject = path.resolve(projectPath);
    if (!(await directoryExists(normalizedProject))) {
      return [];
    }
    const enabled = new Set(providers.filter((provider) => provider.enabled).map((provider) => provider.id));
    const groups = await Promise.all([
      enabled.has("codex") ? this.discoverCodex(normalizedProject) : [],
      enabled.has("claude-code") ? this.discoverClaude(normalizedProject) : [],
      enabled.has("gemini") ? this.discoverGemini(normalizedProject) : []
    ]);
    return groups.flat().sort((left, right) => right.updatedAt - left.updatedAt);
  }

  clearCache(): void {
    this.cache.clear();
    this.codexIndexCache = undefined;
  }

  private async discoverCodex(projectPath: string): Promise<AgentSession[]> {
    const codexHome = path.join(this.homeDirectory, ".codex");
    const sessionsDirectory = path.join(codexHome, "sessions");
    if (!(await directoryExists(sessionsDirectory))) return [];

    const index = await this.readCodexIndex(path.join(codexHome, "session_index.jsonl"));
    const files = await walkFiles(
      sessionsDirectory,
      (filePath) => path.basename(filePath).startsWith("rollout-") && filePath.endsWith(".jsonl")
    );
    const sessions = await Promise.all(
      files.map(async (filePath) => {
        const id = UUID_PATTERN.exec(path.basename(filePath, ".jsonl"))?.[0];
        return this.cachedParse("codex", filePath, projectPath, () => this.parseCodex(filePath, projectPath, id, id ? index.get(id) : undefined));
      })
    );
    return sessions.filter((session): session is AgentSession => session !== undefined);
  }

  private async parseCodex(
    filePath: string,
    projectPath: string,
    nameSessionId?: string,
    indexEntry?: CodexIndexEntry
  ): Promise<AgentSession | undefined> {
    let providerSessionId = nameSessionId;
    let cwd: string | undefined;
    let firstUserMessage: string | undefined;
    let firstTimestamp = 0;
    let lastTimestamp = (await fileFingerprint(filePath))?.modifiedAt ?? Date.now();

    for await (const line of readLines(filePath)) {
      const timestampMatch = /"timestamp"\s*:\s*"([^"]+)"/.exec(line);
      const timestamp = timestampMatch?.[1] ? epochMillis(timestampMatch[1]) : undefined;
      if (timestamp) {
        if (firstTimestamp === 0) firstTimestamp = timestamp;
        lastTimestamp = timestamp;
      }
      if (!line.includes('"session_meta"') && (firstUserMessage || !line.includes('"response_item"'))) {
        continue;
      }
      const json = parseJsonObject(line);
      if (!json) continue;
      if (stringValue(json, "type") === "session_meta") {
        const payload = objectValue(json, "payload");
        providerSessionId = stringValue(payload, "id") ?? providerSessionId;
        cwd = stringValue(payload, "cwd") ?? cwd;
      }
      if (!firstUserMessage && stringValue(json, "type") === "response_item") {
        const payload = objectValue(json, "payload");
        if (stringValue(payload, "role") === "user" && stringValue(payload, "type") === "message") {
          const candidate = textFromContent(payload?.content);
          if (sessionSummary(candidate)) firstUserMessage = candidate;
        }
      }
    }

    if (!providerSessionId || !cwd || !belongsToProject(cwd, projectPath)) return undefined;
    const title =
      indexEntry?.threadName ??
      sessionTitle(firstUserMessage, `Codex session ${providerSessionId.slice(0, 8)}`);
    return this.session({
      id: `codex:${providerSessionId}`,
      projectPath,
      name: title,
      providerId: "codex",
      cwd: path.resolve(cwd),
      providerSessionId,
      historyFilePath: filePath,
      summary: sessionSummary(firstUserMessage),
      createdAt: firstTimestamp || lastTimestamp,
      updatedAt: indexEntry?.updatedAt || lastTimestamp,
      pinned: false
    });
  }

  private async readCodexIndex(filePath: string): Promise<Map<string, CodexIndexEntry>> {
    const fingerprint = await fileFingerprint(filePath);
    if (!fingerprint) return new Map();
    const key = `${fingerprint.modifiedAt}:${fingerprint.size}`;
    if (this.codexIndexCache?.fingerprint === key) return this.codexIndexCache.entries;

    const entries = new Map<string, CodexIndexEntry>();
    for await (const line of readLines(filePath)) {
      const json = parseJsonObject(line);
      const id = stringValue(json, "id");
      if (!id) continue;
      entries.set(id, {
        threadName: stringValue(json, "thread_name")
          ? sessionTitle(stringValue(json, "thread_name"))
          : undefined,
        updatedAt: epochMillis(stringValue(json, "updated_at"))
      });
    }
    this.codexIndexCache = { fingerprint: key, entries };
    return entries;
  }

  private async discoverClaude(projectPath: string): Promise<AgentSession[]> {
    const directoryName = projectPath.replace(/[\\/]/g, "-");
    const historyDirectory = path.join(this.homeDirectory, ".claude", "projects", directoryName);
    if (!(await directoryExists(historyDirectory))) return [];
    const entries = await readdir(historyDirectory, { withFileTypes: true });
    const sessions = await Promise.all(
      entries
        .filter((entry) => entry.isFile() && entry.name.endsWith(".jsonl"))
        .map((entry) => {
          const filePath = path.join(historyDirectory, entry.name);
          return this.cachedParse("claude", filePath, projectPath, () => this.parseClaude(filePath, projectPath));
        })
    );
    return sessions.filter((session): session is AgentSession => session !== undefined);
  }

  private async parseClaude(filePath: string, projectPath: string): Promise<AgentSession | undefined> {
    let providerSessionId = path.basename(filePath, ".jsonl");
    let cwd: string | undefined;
    let firstUserMessage: string | undefined;
    let firstTimestamp = 0;
    let lastTimestamp = (await fileFingerprint(filePath))?.modifiedAt ?? Date.now();

    for await (const line of readLines(filePath)) {
      const timestampMatch = /"timestamp"\s*:\s*"([^"]+)"/.exec(line);
      const timestamp = timestampMatch?.[1] ? epochMillis(timestampMatch[1]) : undefined;
      if (timestamp) {
        if (firstTimestamp === 0) firstTimestamp = timestamp;
        lastTimestamp = timestamp;
      }
      if (!line.includes('"sessionId"') && !line.includes('"cwd"') && (firstUserMessage || !line.includes('"user"'))) {
        continue;
      }
      const json = parseJsonObject(line);
      if (!json) continue;
      providerSessionId = stringValue(json, "sessionId") ?? providerSessionId;
      cwd = stringValue(json, "cwd") ?? cwd;
      if (!firstUserMessage && stringValue(json, "type") === "user") {
        const message = objectValue(json, "message");
        const candidate = textFromContent(json.content) ?? textFromContent(message?.content);
        if (sessionSummary(candidate)) firstUserMessage = candidate;
      }
    }

    const sessionCwd = cwd ? path.resolve(cwd) : projectPath;
    if (!belongsToProject(sessionCwd, projectPath)) return undefined;
    return this.session({
      id: `claude-code:${providerSessionId}`,
      projectPath,
      name: sessionTitle(firstUserMessage, `Claude Code session ${providerSessionId.slice(0, 8)}`),
      providerId: "claude-code",
      cwd: sessionCwd,
      providerSessionId,
      historyFilePath: filePath,
      summary: sessionSummary(firstUserMessage),
      createdAt: firstTimestamp || lastTimestamp,
      updatedAt: lastTimestamp,
      pinned: false
    });
  }

  private async discoverGemini(projectPath: string): Promise<AgentSession[]> {
    const projectHash = createHash("sha256").update(projectPath).digest("hex");
    const chatsDirectory = path.join(this.homeDirectory, ".gemini", "tmp", projectHash, "chats");
    if (!(await directoryExists(chatsDirectory))) return [];
    const entries = await readdir(chatsDirectory, { withFileTypes: true });
    const sessions = await Promise.all(
      entries
        .filter((entry) => entry.isFile() && entry.name.startsWith("session-") && entry.name.endsWith(".json"))
        .map((entry) => {
          const filePath = path.join(chatsDirectory, entry.name);
          return this.cachedParse("gemini", filePath, projectPath, () =>
            this.parseGemini(filePath, projectPath, projectHash)
          );
        })
    );
    return sessions.filter((session): session is AgentSession => session !== undefined);
  }

  private async parseGemini(filePath: string, projectPath: string, projectHash: string): Promise<AgentSession | undefined> {
    let json: JsonRecord | undefined;
    try {
      json = parseJsonObject(await readFile(filePath, "utf8"), 32 * 1024 * 1024);
    } catch {
      return undefined;
    }
    if (!json || stringValue(json, "projectHash") !== projectHash) return undefined;
    const providerSessionId = stringValue(json, "sessionId");
    if (!providerSessionId) return undefined;
    const firstUserMessage = arrayValue(json, "messages")
      ?.map(asRecord)
      .find((message) => stringValue(message, "type") === "user");
    const userText = stringValue(firstUserMessage, "content");
    const storedSummary = stringValue(json, "summary");
    const source = sessionSummary(userText) ? userText : storedSummary;
    const fingerprint = await fileFingerprint(filePath);
    return this.session({
      id: `gemini:${providerSessionId}`,
      projectPath,
      name: sessionTitle(source, `Gemini CLI session ${providerSessionId.slice(0, 8)}`),
      providerId: "gemini",
      cwd: projectPath,
      providerSessionId,
      historyFilePath: filePath,
      summary: sessionSummary(source),
      createdAt: epochMillis(json.startTime) ?? fingerprint?.modifiedAt ?? Date.now(),
      updatedAt: epochMillis(json.lastUpdated) ?? fingerprint?.modifiedAt ?? Date.now(),
      pinned: false
    });
  }

  private async cachedParse(
    provider: string,
    filePath: string,
    projectPath: string,
    parser: () => Promise<AgentSession | undefined>
  ): Promise<AgentSession | undefined> {
    const fingerprint = await fileFingerprint(filePath);
    if (!fingerprint) return undefined;
    const cacheKey = `${provider}:${projectPath}:${filePath}`;
    const fingerprintKey = `${fingerprint.modifiedAt}:${fingerprint.size}`;
    const cached = this.cache.get(cacheKey);
    if (cached?.fingerprint === fingerprintKey) return cached.session;
    const session = await parser();
    this.cache.set(cacheKey, { fingerprint: fingerprintKey, session });
    return session;
  }

  private session(session: AgentSession): AgentSession {
    return session;
  }
}
