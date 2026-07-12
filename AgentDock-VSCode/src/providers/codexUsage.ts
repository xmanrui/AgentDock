import { spawn } from "node:child_process";
import * as path from "node:path";
import * as readline from "node:readline";
import { objectValue, numberValue, parseJsonObject, type JsonRecord } from "../core/json.js";
import type { CliProvider, ProviderUsageSnapshot, ProviderUsageWindow } from "../core/model.js";
import { ProviderDetectionService } from "../services/providerDetection.js";

export interface CodexRateLimitClient {
  read(executablePath: string): Promise<JsonRecord>;
}

export class CodexUsageSource {
  constructor(
    private readonly detection = new ProviderDetectionService(),
    private readonly client: CodexRateLimitClient = new CodexAppServerRateLimitClient()
  ) {}

  async load(provider: CliProvider): Promise<ProviderUsageSnapshot> {
    const detection = await this.detection.detect(provider);
    if (detection.status === "disabled") return unavailable(provider, "Codex is disabled in provider settings.");
    if (detection.status === "missing") return unavailable(provider, "Codex CLI is not available.");
    return parseCodexUsage(provider, await this.client.read(detection.executablePath));
  }
}

export class CodexAppServerRateLimitClient implements CodexRateLimitClient {
  constructor(private readonly timeoutMillis = 8_000) {}

  read(executablePath: string): Promise<JsonRecord> {
    return new Promise((resolve, reject) => {
      const executableDirectory = path.dirname(path.resolve(executablePath));
      const child = spawn(executablePath, ["app-server", "--stdio"], {
        env: { ...process.env, PATH: `${executableDirectory}${path.delimiter}${process.env.PATH ?? ""}` },
        shell: process.platform === "win32",
        windowsHide: true,
        stdio: ["pipe", "pipe", "ignore"]
      });
      const lines = readline.createInterface({ input: child.stdout, crlfDelay: Infinity });
      let settled = false;
      let rateLimitRequested = false;
      const finish = (error?: Error, value?: JsonRecord): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        lines.close();
        child.stdin.end();
        if (!child.killed) child.kill();
        if (error) reject(error);
        else if (value) resolve(value);
        else reject(new Error("Codex app-server returned no rate limits"));
      };
      const timer = setTimeout(() => finish(new Error("Codex usage request timed out")), this.timeoutMillis);
      child.once("error", (error) => finish(error));
      child.stdin.once("error", (error) => finish(error));
      child.once("exit", () => finish(new Error("Codex app-server closed before returning usage limits")));
      lines.on("line", (line) => {
        const message = parseJsonObject(line);
        const requestId = numberValue(message, "id");
        if (requestId === 1 && !rateLimitRequested) {
          rateLimitRequested = true;
          child.stdin.write(`${INITIALIZED_NOTIFICATION}\n${RATE_LIMIT_REQUEST}\n`);
        } else if (requestId === 2) {
          const result = objectValue(message, "result");
          finish(result ? undefined : new Error("Codex app-server rejected the usage request"), result);
        }
      });
      child.stdin.write(`${INITIALIZE_REQUEST}\n`);
    });
  }
}

export function parseCodexUsage(provider: CliProvider, response: JsonRecord): ProviderUsageSnapshot {
  const rateLimits = objectValue(objectValue(response, "rateLimitsByLimitId"), "codex")
    ?? objectValue(response, "rateLimits");
  if (!rateLimits) return unavailable(provider, "Codex did not report plan usage limits.");

  const primary = parseWindow(rateLimits, "primary");
  const secondary = parseWindow(rateLimits, "secondary");
  const windows = [primary, secondary].filter((value): value is ParsedWindow => Boolean(value));
  const fiveHour = windows.find((window) => window.durationMinutes === 300)
    ?? (primary && (primary.durationMinutes === undefined || primary.durationMinutes < 1_440) ? primary : undefined);
  const weekly = windows.find((window) => window.durationMinutes === 10_080)
    ?? (secondary !== fiveHour ? secondary : undefined)
    ?? (primary?.durationMinutes !== undefined && primary.durationMinutes >= 1_440 ? primary : undefined);
  if (!fiveHour && !weekly) return unavailable(provider, "Codex did not report plan usage limits.");

  return {
    providerId: provider.id,
    providerName: provider.displayName,
    status: "available",
    fiveHour: fiveHour?.usage,
    weekly: weekly?.usage,
    resetCount: numberValue(objectValue(response, "rateLimitResetCredits"), "availableCount")
  };
}

interface ParsedWindow {
  usage: ProviderUsageWindow;
  durationMinutes?: number;
}

function parseWindow(rateLimits: JsonRecord, name: string): ParsedWindow | undefined {
  const value = objectValue(rateLimits, name);
  const usedPercent = numberValue(value, "usedPercent");
  if (usedPercent === undefined) return undefined;
  return {
    usage: {
      usedPercent: Math.max(0, Math.min(100, Math.round(usedPercent))),
      resetsAtEpochSeconds: numberValue(value, "resetsAt")
    },
    durationMinutes: numberValue(value, "windowDurationMins")
  };
}

function unavailable(provider: CliProvider, message: string): ProviderUsageSnapshot {
  return { providerId: provider.id, providerName: provider.displayName, status: "unavailable", message };
}

const INITIALIZE_REQUEST = JSON.stringify({
  id: 1,
  method: "initialize",
  params: { clientInfo: { name: "agentdock", version: "1" }, capabilities: { experimentalApi: true } }
});
const INITIALIZED_NOTIFICATION = JSON.stringify({ method: "initialized" });
const RATE_LIMIT_REQUEST = JSON.stringify({ id: 2, method: "account/rateLimits/read", params: null });
