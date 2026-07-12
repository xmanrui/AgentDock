import { open, readFile, stat } from "node:fs/promises";
import { arrayValue, asRecord, booleanValue, objectValue, parseJsonObject, stringValue } from "../core/json.js";
import type { ProviderId } from "../core/model.js";

export type TerminalActivityEvent = "started" | "completed";

export function parseTerminalActivityEvent(providerId: ProviderId, line: string): TerminalActivityEvent | undefined {
  const json = parseJsonObject(line);
  if (!json) return undefined;
  if (providerId === "codex") {
    if (stringValue(json, "type") !== "event_msg") return undefined;
    const type = stringValue(objectValue(json, "payload"), "type");
    if (type === "task_started") return "started";
    if (type === "task_complete" || type === "turn_aborted") return "completed";
    return undefined;
  }
  if (providerId === "claude-code") {
    if (stringValue(json, "type") === "system" && stringValue(json, "subtype") === "turn_duration") {
      return "completed";
    }
    if (stringValue(json, "type") !== "user" || booleanValue(json, "isMeta") === true) return undefined;
    const message = objectValue(json, "message");
    const role = stringValue(message, "role");
    if (role && role !== "user") return undefined;
    return hasPromptText(message?.content ?? json.content) ? "started" : undefined;
  }
  return undefined;
}

export class LocalTerminalActivityMonitor {
  private offset = 0;
  private geminiMessageCount = 0;
  private geminiFingerprint = "";
  private timer?: NodeJS.Timeout;
  private polling = false;
  private readonly decoder = new BoundedUtf8LineDecoder(1_048_576);

  constructor(
    private readonly providerId: ProviderId,
    private readonly historyFilePath: string,
    private readonly onEvent: (event: TerminalActivityEvent) => void,
    private readonly pollIntervalMillis = 350
  ) {}

  async start(): Promise<void> {
    if (this.timer || !this.historyFilePath) return;
    const details = await stat(this.historyFilePath).catch(() => undefined);
    this.offset = details?.isFile() ? details.size : 0;
    if (this.providerId === "gemini") {
      const messages = await this.readGeminiMessages();
      this.geminiMessageCount = messages?.length ?? 0;
      this.geminiFingerprint = details ? `${details.mtimeMs}:${details.size}` : "";
    }
    this.timer = setInterval(() => void this.poll(), this.pollIntervalMillis);
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }

  private async poll(): Promise<void> {
    if (this.polling) return;
    this.polling = true;
    try {
      if (this.providerId === "gemini") await this.readGeminiActivity();
      else await this.readAppendedLines();
    } catch {
      // Histories are written concurrently by provider CLIs; retry on the next poll.
    } finally {
      this.polling = false;
    }
  }

  private async readGeminiActivity(): Promise<void> {
    const details = await stat(this.historyFilePath).catch(() => undefined);
    if (!details?.isFile()) return;
    const fingerprint = `${details.mtimeMs}:${details.size}`;
    if (fingerprint === this.geminiFingerprint) return;
    const messages = await this.readGeminiMessages();
    if (!messages) return;
    if (messages.length < this.geminiMessageCount) this.geminiMessageCount = 0;
    for (const rawMessage of messages.slice(this.geminiMessageCount)) {
      const type = stringValue(asRecord(rawMessage), "type");
      if (type === "user") this.onEvent("started");
      if (type === "gemini") this.onEvent("completed");
    }
    this.geminiMessageCount = messages.length;
    this.geminiFingerprint = fingerprint;
  }

  private async readGeminiMessages(): Promise<unknown[] | undefined> {
    try {
      return arrayValue(parseJsonObject(await readFile(this.historyFilePath, "utf8"), 32 * 1024 * 1024), "messages");
    } catch {
      return undefined;
    }
  }

  private async readAppendedLines(): Promise<void> {
    const details = await stat(this.historyFilePath).catch(() => undefined);
    if (!details?.isFile()) return;
    if (details.size < this.offset) {
      this.offset = 0;
      this.decoder.reset();
    }
    if (details.size <= this.offset) return;

    const bytesToRead = Math.min(details.size - this.offset, 8_388_608);
    const buffer = Buffer.allocUnsafe(Math.min(bytesToRead, 65_536));
    const handle = await open(this.historyFilePath, "r");
    try {
      let remaining = bytesToRead;
      while (remaining > 0) {
        const length = Math.min(remaining, buffer.length);
        const result = await handle.read(buffer, 0, length, this.offset);
        if (result.bytesRead <= 0) break;
        this.offset += result.bytesRead;
        remaining -= result.bytesRead;
        for (const line of this.decoder.accept(buffer.subarray(0, result.bytesRead))) {
          const event = parseTerminalActivityEvent(this.providerId, line);
          if (event) this.onEvent(event);
        }
      }
    } finally {
      await handle.close();
    }
  }
}

export class BoundedUtf8LineDecoder {
  private pending = Buffer.alloc(0);
  private discardingOversizedLine = false;

  constructor(private readonly maximumLineBytes: number) {
    if (maximumLineBytes <= 0) throw new Error("maximumLineBytes must be positive");
  }

  accept(bytes: Uint8Array): string[] {
    const lines: string[] = [];
    let segmentStart = 0;
    for (let index = 0; index < bytes.length; index += 1) {
      if (bytes[index] !== 0x0a) continue;
      if (!this.discardingOversizedLine) this.append(bytes.subarray(segmentStart, index));
      if (!this.discardingOversizedLine) lines.push(this.pending.toString("utf8").replace(/\r$/, ""));
      this.pending = Buffer.alloc(0);
      this.discardingOversizedLine = false;
      segmentStart = index + 1;
    }
    if (segmentStart < bytes.length && !this.discardingOversizedLine) this.append(bytes.subarray(segmentStart));
    return lines;
  }

  reset(): void {
    this.pending = Buffer.alloc(0);
    this.discardingOversizedLine = false;
  }

  private append(bytes: Uint8Array): void {
    if (!bytes.length) return;
    if (this.pending.length + bytes.length > this.maximumLineBytes) {
      this.pending = Buffer.alloc(0);
      this.discardingOversizedLine = true;
      return;
    }
    this.pending = Buffer.concat([this.pending, bytes]);
  }
}

function hasPromptText(content: unknown): boolean {
  if (typeof content === "string") return Boolean(content.trim());
  if (!Array.isArray(content)) return false;
  return content.some((rawBlock) => {
    const block = asRecord(rawBlock);
    const type = stringValue(block, "type");
    return (type === undefined || type === "text" || type === "input_text") && Boolean(stringValue(block, "text")?.trim());
  });
}
