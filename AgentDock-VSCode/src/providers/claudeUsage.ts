import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { epochMillis, objectValue, parseJsonObject, stringValue, type JsonRecord } from "../core/json.js";
import type { CliProvider, ProviderUsageSnapshot, ProviderUsageWindow } from "../core/model.js";
import { runProcess } from "../services/providerDetection.js";

export interface ClaudeUsageConfiguration {
  usageUrl: string;
  bearerToken: string;
}

export interface ClaudeSecureCredentialsReader {
  read(configDirectory: string, customConfigDirectory: boolean): Promise<string | undefined>;
}

export interface ClaudeUsageHttpResponse {
  statusCode: number;
  body: string;
}

export interface ClaudeUsageClient {
  read(configuration: ClaudeUsageConfiguration): Promise<ClaudeUsageHttpResponse>;
}

export class ClaudeUsageSource {
  constructor(
    private readonly resolver = new ClaudeUsageConfigurationResolver(),
    private readonly client: ClaudeUsageClient = new ClaudeUsageHttpClient()
  ) {}

  async load(provider: CliProvider): Promise<ProviderUsageSnapshot> {
    if (!provider.enabled) return unavailable(provider, "Claude Code is disabled in provider settings.");
    const configuration = await this.resolver.resolve();
    if (!configuration) return unauthenticated(provider, "Claude Code usage credentials are not available to AgentDock.");
    const response = await this.client.read(configuration);
    if (response.statusCode >= 200 && response.statusCode < 300) return parseClaudeUsage(provider, response.body);
    if (response.statusCode === 401 || response.statusCode === 403) {
      return unauthenticated(provider, "Sign in to Claude Code with a subscription account to view usage.");
    }
    if (response.statusCode === 404) return unavailable(provider, "The current Claude endpoint does not expose plan usage.");
    return unavailable(provider, "Could not load Claude usage limits right now.");
  }
}

export class ClaudeUsageConfigurationResolver {
  private readonly environment: NodeJS.ProcessEnv;
  private readonly homeDirectory: string;
  private readonly secureCredentialsReader: ClaudeSecureCredentialsReader;

  constructor(options: {
    environment?: NodeJS.ProcessEnv;
    homeDirectory?: string;
    secureCredentialsReader?: ClaudeSecureCredentialsReader;
  } = {}) {
    this.environment = options.environment ?? process.env;
    this.homeDirectory = options.homeDirectory ?? os.homedir();
    this.secureCredentialsReader = options.secureCredentialsReader ?? new MacClaudeKeychainCredentialsReader(this.environment);
  }

  async resolve(): Promise<ClaudeUsageConfiguration | undefined> {
    const configuredDirectory = nonBlank(this.environment.CLAUDE_CONFIG_DIR);
    const configDirectory = configuredDirectory ? path.resolve(configuredDirectory) : path.join(this.homeDirectory, ".claude");
    const settingsEnvironment = await readSettingsEnvironment(path.join(configDirectory, "settings.json"));
    const firstValue = (name: string): string | undefined => nonBlank(this.environment[name]) ?? nonBlank(settingsEnvironment[name]);
    const baseUrl = firstValue("ANTHROPIC_BASE_URL") ?? "https://api.anthropic.com";
    const explicitToken = firstValue("CLAUDE_CODE_OAUTH_TOKEN") ?? firstValue("ANTHROPIC_AUTH_TOKEN");
    const storedToken = isOfficialAnthropicBaseUrl(baseUrl)
      ? await this.storedCredentialsToken(configDirectory, Boolean(configuredDirectory))
      : undefined;
    const bearerToken = explicitToken ?? storedToken;
    const usageUrl = usageUrlFromBase(baseUrl);
    return bearerToken && usageUrl ? { usageUrl, bearerToken } : undefined;
  }

  private async storedCredentialsToken(configDirectory: string, custom: boolean): Promise<string | undefined> {
    const credentials = await readJson(path.join(configDirectory, ".credentials.json"));
    const direct = credentialsToken(credentials);
    if (direct) return direct;
    const securePayload = await this.secureCredentialsReader.read(configDirectory, custom);
    return securePayload ? credentialsToken(parseJsonObject(securePayload)) : undefined;
  }
}

export class MacClaudeKeychainCredentialsReader implements ClaudeSecureCredentialsReader {
  private readonly accountName: string;

  constructor(private readonly environment: NodeJS.ProcessEnv = process.env) {
    const candidate = environment.USER ?? os.userInfo().username;
    this.accountName = /^[A-Za-z0-9._-]+$/.test(candidate) ? candidate : "claude-code-user";
  }

  async read(configDirectory: string, customConfigDirectory: boolean): Promise<string | undefined> {
    if (process.platform !== "darwin") return undefined;
    const suffix = customConfigDirectory ? `-${storageHash(configDirectory)}` : "";
    const result = await runProcess(
      "/usr/bin/security",
      ["find-generic-password", "-a", this.accountName, "-w", "-s", `Claude Code-credentials${suffix}`],
      2_000,
      this.environment,
      true
    );
    return result.exitCode === 0 ? nonBlank(result.stdout) : undefined;
  }
}

export class ClaudeUsageHttpClient implements ClaudeUsageClient {
  async read(configuration: ClaudeUsageConfiguration): Promise<ClaudeUsageHttpResponse> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6_000);
    try {
      const response = await fetch(configuration.usageUrl, {
        method: "GET",
        signal: controller.signal,
        headers: {
          Authorization: `Bearer ${configuration.bearerToken}`,
          "Content-Type": "application/json",
          "anthropic-beta": "oauth-2025-04-20",
          "User-Agent": "AgentDock"
        }
      });
      return { statusCode: response.status, body: await response.text() };
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function parseClaudeUsage(provider: CliProvider, body: string): ProviderUsageSnapshot {
  const response = parseJsonObject(body);
  if (!response) return unavailable(provider, "Claude returned an unreadable usage response.");
  const fiveHour = parseWindow(response, "five_hour");
  const weekly = parseWindow(response, "seven_day");
  if (!fiveHour && !weekly) return unavailable(provider, "Claude did not report plan usage limits.");
  return {
    providerId: provider.id,
    providerName: provider.displayName,
    status: "available",
    fiveHour,
    weekly
  };
}

function parseWindow(response: JsonRecord, name: string): ProviderUsageWindow | undefined {
  const window = objectValue(response, name);
  const rawUtilization = window?.utilization ?? window?.used_percentage;
  const utilization = typeof rawUtilization === "number" && Number.isFinite(rawUtilization) ? rawUtilization : undefined;
  if (utilization === undefined) return undefined;
  return {
    usedPercent: Math.max(0, Math.min(100, Math.round(utilization))),
    resetsAtEpochSeconds: toEpochSeconds(window?.resets_at)
  };
}

function toEpochSeconds(value: unknown): number | undefined {
  const millis = epochMillis(value);
  return millis === undefined ? undefined : Math.floor(millis / 1_000);
}

async function readSettingsEnvironment(settingsPath: string): Promise<Record<string, string>> {
  const root = await readJson(settingsPath);
  const environment = objectValue(root, "env");
  if (!environment) return {};
  return Object.fromEntries(
    Object.entries(environment).filter((entry): entry is [string, string] => typeof entry[1] === "string")
  );
}

async function readJson(filePath: string): Promise<JsonRecord | undefined> {
  try {
    return parseJsonObject(await readFile(filePath, "utf8"));
  } catch {
    return undefined;
  }
}

function credentialsToken(root?: JsonRecord): string | undefined {
  const oauth = objectValue(root, "claudeAiOauth") ?? root;
  return nonBlank(stringValue(oauth, "accessToken")) ?? nonBlank(stringValue(oauth, "access_token"));
}

function usageUrlFromBase(rawBaseUrl: string): string | undefined {
  try {
    const base = new URL(rawBaseUrl.trim());
    const secure = base.protocol === "https:";
    const loopback = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(base.hostname.toLowerCase());
    if (!secure && !(base.protocol === "http:" && loopback)) return undefined;
    return new URL("/api/oauth/usage", base).toString();
  } catch {
    return undefined;
  }
}

function isOfficialAnthropicBaseUrl(rawBaseUrl: string): boolean {
  try {
    const url = new URL(rawBaseUrl.trim());
    return url.protocol === "https:" && url.hostname.toLowerCase() === "api.anthropic.com";
  } catch {
    return false;
  }
}

function storageHash(directory: string): string {
  return createHash("sha256").update(path.resolve(directory).normalize("NFC")).digest("hex").slice(0, 8);
}

function nonBlank(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function unavailable(provider: CliProvider, message: string): ProviderUsageSnapshot {
  return { providerId: provider.id, providerName: provider.displayName, status: "unavailable", message };
}

function unauthenticated(provider: CliProvider, message: string): ProviderUsageSnapshot {
  return { providerId: provider.id, providerName: provider.displayName, status: "unauthenticated", message };
}
