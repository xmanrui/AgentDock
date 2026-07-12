import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import {
  arrayValue,
  asRecord,
  booleanValue,
  objectValue,
  parseJsonObject,
  stringValue,
  type JsonRecord
} from "../core/json.js";
import { fileFingerprint, readLines, walkFiles } from "../core/files.js";
import type { AgentSession, SessionMessage, SessionPreview, SessionRole } from "../core/model.js";
import { sanitizePreviewMessage, sessionSummary } from "../core/text.js";

interface CachedPreview {
  fingerprint: string;
  preview: SessionPreview;
}

const CODEX_SESSION_ID = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/;
const TEXT_CONTENT_TYPES = new Set(["text", "input_text", "output_text"]);

export class SessionContentService {
  private readonly previews = new Map<string, CachedPreview>();
  private readonly codexSources = new Map<string, string>();
  private lastCodexSourceScanAt = 0;

  constructor(
    private readonly homeDirectory = os.homedir(),
    private recentMessageLimit = 9,
    private readonly now = () => Date.now()
  ) {}

  setRecentMessageLimit(limit: number): void {
    const normalized = Math.max(1, Math.min(100, Math.floor(limit)));
    if (normalized === this.recentMessageLimit) return;
    this.recentMessageLimit = normalized;
    this.previews.clear();
  }

  async load(session: AgentSession): Promise<SessionPreview> {
    const source = await this.locateHistoryFile(session);
    if (!source) return fallbackPreview(session, "Full local conversation is not available yet.");

    const fingerprint = await fileFingerprint(source);
    if (!fingerprint) return fallbackPreview(session, "Full local conversation is not available yet.");
    const key = `${fingerprint.modifiedAt}:${fingerprint.size}`;
    const cached = this.previews.get(source);
    if (cached?.fingerprint === key) return cached.preview;

    let preview: SessionPreview;
    try {
      preview = session.providerId === "gemini"
        ? await this.parseGemini(source)
        : await this.parseJsonLines(source, session.providerId);
    } catch {
      preview = fallbackPreview(session, "The local conversation could not be read.");
    }
    this.previews.set(source, { fingerprint: key, preview });
    return preview;
  }

  async locateHistoryFile(session: AgentSession): Promise<string | undefined> {
    if (session.historyFilePath && (await fileFingerprint(session.historyFilePath))) {
      return session.historyFilePath;
    }
    if (!isSafeFileName(session.providerSessionId)) return undefined;

    if (session.providerId === "codex") return this.findCodexSource(session.providerSessionId);
    if (session.providerId === "claude-code") return this.findClaudeSource(session);
    if (session.providerId === "gemini") return this.findGeminiSource(session);
    return undefined;
  }

  clearCache(): void {
    this.previews.clear();
    this.codexSources.clear();
    this.lastCodexSourceScanAt = 0;
  }

  private async findCodexSource(sessionId: string): Promise<string | undefined> {
    const existing = this.codexSources.get(sessionId);
    if (existing && (await fileFingerprint(existing))) return existing;

    if (this.now() - this.lastCodexSourceScanAt >= 30_000 || this.lastCodexSourceScanAt === 0) {
      const root = path.join(this.homeDirectory, ".codex", "sessions");
      const files = await walkFiles(
        root,
        (candidate) => path.basename(candidate).startsWith("rollout-") && candidate.endsWith(".jsonl"),
        8
      );
      for (const candidate of files) {
        const id = CODEX_SESSION_ID.exec(path.basename(candidate, ".jsonl"))?.[0];
        if (!id) continue;
        const previous = this.codexSources.get(id);
        const previousFingerprint = previous ? await fileFingerprint(previous) : undefined;
        const candidateFingerprint = await fileFingerprint(candidate);
        if (!previousFingerprint || (candidateFingerprint?.modifiedAt ?? 0) > previousFingerprint.modifiedAt) {
          this.codexSources.set(id, candidate);
        }
      }
      this.lastCodexSourceScanAt = this.now();
    }
    return this.codexSources.get(sessionId);
  }

  private async findClaudeSource(session: AgentSession): Promise<string | undefined> {
    const encodedProject = path.resolve(session.projectPath).replace(/[\\/]/g, "-");
    const root = path.join(this.homeDirectory, ".claude", "projects");
    const direct = path.join(root, encodedProject, `${session.providerSessionId}.jsonl`);
    if (await fileFingerprint(direct)) return direct;

    let directories;
    try {
      directories = await readdir(root, { withFileTypes: true });
    } catch {
      return undefined;
    }
    for (const directory of directories) {
      if (!directory.isDirectory()) continue;
      const candidate = path.join(root, directory.name, `${session.providerSessionId}.jsonl`);
      if (await fileFingerprint(candidate)) return candidate;
    }
    return undefined;
  }

  private async findGeminiSource(session: AgentSession): Promise<string | undefined> {
    const projectHash = createHash("sha256").update(path.resolve(session.projectPath)).digest("hex");
    const root = path.join(this.homeDirectory, ".gemini", "tmp", projectHash, "chats");
    let files;
    try {
      files = await readdir(root, { withFileTypes: true });
    } catch {
      return undefined;
    }
    for (const file of files) {
      if (!file.isFile() || !file.name.startsWith("session-") || !file.name.endsWith(".json")) continue;
      const candidate = path.join(root, file.name);
      try {
        const json = parseJsonObject(await readFile(candidate, "utf8"), 32 * 1024 * 1024);
        if (stringValue(json, "sessionId") === session.providerSessionId) return candidate;
      } catch {
        // Keep scanning other histories.
      }
    }
    return undefined;
  }

  private async parseJsonLines(source: string, providerId: AgentSession["providerId"]): Promise<SessionPreview> {
    const retained = new RetainedMessages(this.recentMessageLimit);
    for await (const line of readLines(source)) {
      const json = parseJsonObject(line);
      if (!json) continue;
      const message = providerId === "codex" ? parseCodexMessage(json) : parseClaudeMessage(json);
      if (message) retained.add(message);
    }
    return retained.toPreview();
  }

  private async parseGemini(source: string): Promise<SessionPreview> {
    const json = parseJsonObject(await readFile(source, "utf8"), 32 * 1024 * 1024);
    if (!json) return { messages: [], omittedMessageCount: 0, notice: "The local Gemini CLI conversation could not be read." };
    const retained = new RetainedMessages(this.recentMessageLimit);
    for (const rawMessage of arrayValue(json, "messages") ?? []) {
      const message = asRecord(rawMessage);
      const type = stringValue(message, "type");
      const role: SessionRole | undefined = type === "user" ? "user" : type === "gemini" ? "assistant" : undefined;
      const parsed = role ? createMessage(role, stringValue(message, "content")) : undefined;
      if (parsed) retained.add(parsed);
    }
    return retained.toPreview();
  }
}

function parseCodexMessage(json: JsonRecord): SessionMessage | undefined {
  if (stringValue(json, "type") !== "response_item") return undefined;
  const payload = objectValue(json, "payload");
  if (stringValue(payload, "type") !== "message") return undefined;
  const role = toRole(stringValue(payload, "role"));
  return role ? createMessage(role, textFromMessageContent(payload?.content)) : undefined;
}

function parseClaudeMessage(json: JsonRecord): SessionMessage | undefined {
  if (booleanValue(json, "isMeta") === true) return undefined;
  const outerRole = toRole(stringValue(json, "type"));
  if (!outerRole) return undefined;
  const message = objectValue(json, "message");
  const role = toRole(stringValue(message, "role")) ?? outerRole;
  return createMessage(role, textFromMessageContent(message?.content ?? json.content));
}

function createMessage(role: SessionRole, rawText?: string): SessionMessage | undefined {
  const text = sanitizePreviewMessage(rawText, Number.MAX_SAFE_INTEGER);
  if (!text) return undefined;
  return { role, text: text.length > 2_200 ? `${text.slice(0, 2_200).trimEnd()}...` : text };
}

function textFromMessageContent(content: unknown): string | undefined {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    const parts = content.map(textFromContentBlock).filter((value): value is string => Boolean(value?.trim()));
    return parts.length ? parts.join("\n") : undefined;
  }
  return textFromContentBlock(content);
}

function textFromContentBlock(value: unknown): string | undefined {
  if (typeof value === "string") return value;
  const block = asRecord(value);
  if (!block) return undefined;
  const type = stringValue(block, "type");
  if (type && !TEXT_CONTENT_TYPES.has(type)) return undefined;
  return stringValue(block, "text") ?? textFromMessageContent(block.content);
}

function toRole(value?: string): SessionRole | undefined {
  return value === "user" || value === "assistant" ? value : undefined;
}

function fallbackPreview(session: AgentSession, notice: string): SessionPreview {
  const summary = sessionSummary(session.summary) || sessionSummary(session.name);
  return {
    messages: summary ? [{ role: "user", text: summary }] : [],
    omittedMessageCount: 0,
    notice
  };
}

function isSafeFileName(value: string): boolean {
  return Boolean(value) && value !== "." && value !== ".." && !/[\\/]/.test(value);
}

class RetainedMessages {
  private first?: SessionMessage;
  private readonly recent: SessionMessage[] = [];
  private acceptedCount = 0;
  private previous?: SessionMessage;

  constructor(private readonly recentLimit: number) {}

  add(message: SessionMessage): void {
    if (this.previous?.role === message.role && this.previous.text === message.text) return;
    this.previous = message;
    this.acceptedCount += 1;
    if (!this.first) {
      this.first = message;
      return;
    }
    if (this.recent.length === this.recentLimit) this.recent.shift();
    this.recent.push(message);
  }

  toPreview(): SessionPreview {
    const messages = this.first ? [this.first, ...this.recent] : [];
    return messages.length
      ? { messages, omittedMessageCount: Math.max(0, this.acceptedCount - messages.length) }
      : { messages: [], omittedMessageCount: 0, notice: "No readable conversation messages were found." };
  }
}
