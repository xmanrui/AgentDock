import { readFile } from "node:fs/promises";
import { fileFingerprint, readLines } from "../core/files.js";
import {
  arrayValue,
  asRecord,
  epochMillis,
  nonNegativeNumber,
  objectValue,
  parseJsonObject,
  stringValue
} from "../core/json.js";
import type { AgentSession, ProviderId, SessionMetrics } from "../core/model.js";
import { SessionContentService } from "./sessionContent.js";

interface CachedMetrics {
  fingerprint: string;
  anchorDate: string;
  metrics: SessionMetrics;
}

interface TokenSample {
  tokens: number;
  date: string;
}

const DAY_COUNT = 7;
const CLAUDE_TOKEN_FIELDS = [
  "input_tokens",
  "output_tokens",
  "cache_creation_input_tokens",
  "cache_read_input_tokens"
] as const;

export class SessionMetricsService {
  private readonly bySource = new Map<string, CachedMetrics>();
  private readonly bySession = new Map<string, CachedMetrics>();

  constructor(
    private readonly contentService = new SessionContentService(),
    private readonly now = () => Date.now(),
    private readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
  ) {}

  cached(session: AgentSession): SessionMetrics {
    const cached = this.bySession.get(session.id);
    return cached?.anchorDate === this.anchorDate() ? cached.metrics : unavailableMetrics();
  }

  prime(session: AgentSession, metrics: SessionMetrics, anchorDate = this.currentAnchorDate()): void {
    this.bySession.set(session.id, { fingerprint: "persisted", anchorDate, metrics });
  }

  currentAnchorDate(): string {
    return this.anchorDate();
  }

  cachedProviderTotal(sessions: Iterable<AgentSession>, providerId: ProviderId): number | null {
    let found = false;
    let total = 0;
    for (const session of sessions) {
      if (session.providerId !== providerId) continue;
      const sessionTotal = this.cached(session).totalTokens;
      if (sessionTotal === null) continue;
      found = true;
      total = safeAdd(total, sessionTotal);
    }
    return found ? total : null;
  }

  async load(session: AgentSession): Promise<SessionMetrics> {
    const source = await this.contentService.locateHistoryFile(session);
    if (!source) return this.cached(session);
    const fingerprint = await fileFingerprint(source);
    if (!fingerprint) return this.cached(session);

    const anchorDate = this.anchorDate();
    const fingerprintKey = `${fingerprint.modifiedAt}:${fingerprint.size}`;
    const cacheKey = `${session.providerId}:${source}`;
    const cached = this.bySource.get(cacheKey);
    if (cached?.fingerprint === fingerprintKey && cached.anchorDate === anchorDate) {
      this.bySession.set(session.id, cached);
      return cached.metrics;
    }

    let metrics = unavailableMetrics();
    try {
      if (session.providerId === "codex") metrics = await this.parseCodex(source, fingerprint.modifiedAt, anchorDate);
      if (session.providerId === "claude-code") metrics = await this.parseClaude(source, fingerprint.modifiedAt, anchorDate);
      if (session.providerId === "gemini") metrics = await this.parseGemini(source, fingerprint.modifiedAt, anchorDate);
    } catch {
      metrics = unavailableMetrics();
    }
    const entry = { fingerprint: fingerprintKey, anchorDate, metrics };
    this.bySource.set(cacheKey, entry);
    this.bySession.set(session.id, entry);
    return metrics;
  }

  clearCache(): void {
    this.bySource.clear();
    this.bySession.clear();
  }

  private async parseCodex(source: string, modifiedAt: number, anchorDate: string): Promise<SessionMetrics> {
    const dailyTotals = new Map<string, number>();
    const responseTimes = new DailyResponseTimes(this.timeZone);
    const fallbackDate = dateKey(modifiedAt, this.timeZone) ?? anchorDate;
    let previousCumulative: number | undefined;
    let totalTokens = 0;
    let foundUsage = false;

    for await (const line of readLines(source)) {
      if (!line.includes('"token_count"') && !line.includes('"task_complete"')) continue;
      const json = parseJsonObject(line);
      if (!json) continue;
      const payload = objectValue(json, "payload");
      if (stringValue(json, "type") !== "event_msg" || !payload) continue;

      if (stringValue(payload, "type") === "token_count") {
        const info = objectValue(payload, "info");
        const cumulative = nonNegativeNumber(objectValue(info, "total_token_usage"), "total_tokens");
        const lastUsage = nonNegativeNumber(objectValue(info, "last_token_usage"), "total_tokens");
        if (cumulative === undefined && lastUsage === undefined) continue;
        foundUsage = true;
        let delta = lastUsage ?? 0;
        if (cumulative !== undefined) {
          delta = previousCumulative === undefined || cumulative < previousCumulative
            ? cumulative
            : cumulative - previousCumulative;
          previousCumulative = cumulative;
        }
        totalTokens = safeAdd(totalTokens, delta);
        const date = dateKey(epochMillis(json.timestamp), this.timeZone) ?? fallbackDate;
        dailyTotals.set(date, safeAdd(dailyTotals.get(date) ?? 0, delta));
      } else if (stringValue(payload, "type") === "task_complete") {
        const duration = nonNegativeNumber(payload, "duration_ms");
        if (duration !== undefined) responseTimes.record(duration, epochMillis(json.timestamp));
      }
    }
    return buildMetrics(foundUsage, totalTokens, dailyTotals, responseTimes, anchorDate);
  }

  private async parseClaude(source: string, modifiedAt: number, anchorDate: string): Promise<SessionMetrics> {
    const samples = new Map<string, TokenSample>();
    const responseTimes = new DailyResponseTimes(this.timeZone);
    const fallbackDate = dateKey(modifiedAt, this.timeZone) ?? anchorDate;
    let anonymousIndex = 0;
    let foundUsage = false;

    for await (const line of readLines(source)) {
      if (!line.includes('"assistant"') && !line.includes('"turn_duration"')) continue;
      const json = parseJsonObject(line);
      const type = stringValue(json, "type");
      const timestamp = epochMillis(json?.timestamp);
      if (type === "system" && stringValue(json, "subtype") === "turn_duration") {
        const duration = nonNegativeNumber(json, "durationMs");
        if (duration !== undefined) responseTimes.record(duration, timestamp);
        continue;
      }
      if (type !== "assistant") continue;
      const message = objectValue(json, "message");
      const usage = objectValue(message, "usage") ?? objectValue(json, "usage");
      const values = CLAUDE_TOKEN_FIELDS.map((field) => nonNegativeNumber(usage, field)).filter(
        (value): value is number => value !== undefined
      );
      if (!values.length) continue;

      foundUsage = true;
      const tokens = values.reduce(safeAdd, 0);
      const sample = { tokens, date: dateKey(timestamp, this.timeZone) ?? fallbackDate };
      const messageId = stringValue(message, "id");
      const requestId = stringValue(json, "requestId");
      const key = messageId ? `message:${messageId}` : requestId ? `request:${requestId}` : `line:${anonymousIndex++}`;
      const existing = samples.get(key);
      if (!existing || sample.tokens > existing.tokens) samples.set(key, sample);
    }

    const dailyTotals = new Map<string, number>();
    let totalTokens = 0;
    for (const sample of samples.values()) {
      totalTokens = safeAdd(totalTokens, sample.tokens);
      dailyTotals.set(sample.date, safeAdd(dailyTotals.get(sample.date) ?? 0, sample.tokens));
    }
    return buildMetrics(foundUsage, totalTokens, dailyTotals, responseTimes, anchorDate);
  }

  private async parseGemini(source: string, modifiedAt: number, anchorDate: string): Promise<SessionMetrics> {
    const json = parseJsonObject(await readFile(source, "utf8"), 32 * 1024 * 1024);
    if (!json) return unavailableMetrics();
    const dailyTotals = new Map<string, number>();
    const responseTimes = new DailyResponseTimes(this.timeZone);
    const fallbackDate = dateKey(modifiedAt, this.timeZone) ?? anchorDate;
    let totalTokens = 0;
    let foundUsage = false;
    let pendingUserAt: number | undefined;

    for (const rawMessage of arrayValue(json, "messages") ?? []) {
      const message = asRecord(rawMessage);
      const timestamp = epochMillis(message?.timestamp);
      if (stringValue(message, "type") === "user") {
        pendingUserAt = timestamp;
        continue;
      }
      if (stringValue(message, "type") !== "gemini") continue;
      const tokens = nonNegativeNumber(objectValue(message, "tokens"), "total");
      if (tokens !== undefined) {
        foundUsage = true;
        totalTokens = safeAdd(totalTokens, tokens);
        const date = dateKey(timestamp, this.timeZone) ?? fallbackDate;
        dailyTotals.set(date, safeAdd(dailyTotals.get(date) ?? 0, tokens));
      }
      if (pendingUserAt !== undefined && timestamp !== undefined && timestamp >= pendingUserAt) {
        responseTimes.record(timestamp - pendingUserAt, timestamp);
      }
      pendingUserAt = undefined;
    }
    return buildMetrics(foundUsage, totalTokens, dailyTotals, responseTimes, anchorDate);
  }

  private anchorDate(): string {
    return dateKey(this.now(), this.timeZone) ?? "1970-01-01";
  }
}

function buildMetrics(
  foundUsage: boolean,
  totalTokens: number,
  dailyTotals: Map<string, number>,
  responseTimes: DailyResponseTimes,
  anchorDate: string
): SessionMetrics {
  const dates = lastDateKeys(anchorDate, DAY_COUNT);
  return {
    totalTokens: foundUsage ? totalTokens : null,
    dailyTokens: dates.map((date) => dailyTotals.get(date) ?? 0),
    dailyAverageResponseMillis: dates.map((date) => responseTimes.average(date))
  };
}

export function unavailableMetrics(): SessionMetrics {
  return {
    totalTokens: null,
    dailyTokens: Array.from({ length: DAY_COUNT }, () => 0),
    dailyAverageResponseMillis: Array.from({ length: DAY_COUNT }, () => null)
  };
}

class DailyResponseTimes {
  private readonly values = new Map<string, { total: number; count: number }>();

  constructor(private readonly timeZone: string) {}

  record(durationMillis: number, completedAt?: number): void {
    const date = dateKey(completedAt, this.timeZone);
    if (!date) return;
    const current = this.values.get(date) ?? { total: 0, count: 0 };
    current.total = safeAdd(current.total, durationMillis);
    current.count += 1;
    this.values.set(date, current);
  }

  average(date: string): number | null {
    const value = this.values.get(date);
    return value?.count ? Math.floor(value.total / value.count) : null;
  }
}

function dateKey(timestamp: unknown, timeZone: string): string | undefined {
  if (typeof timestamp !== "number" || !Number.isFinite(timestamp) || timestamp <= 0) return undefined;
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date(timestamp));
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return year && month && day ? `${year}-${month}-${day}` : undefined;
}

function lastDateKeys(anchorDate: string, count: number): string[] {
  const [year, month, day] = anchorDate.split("-").map(Number);
  const anchor = Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1);
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(anchor - (count - 1 - index) * 86_400_000);
    return date.toISOString().slice(0, 10);
  });
}

function safeAdd(left: number, right: number): number {
  return Math.min(Number.MAX_SAFE_INTEGER, left + right);
}
