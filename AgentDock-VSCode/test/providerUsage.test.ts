import { chmod, mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { ClaudeUsageConfigurationResolver, parseClaudeUsage } from "../src/providers/claudeUsage.js";
import { CodexAppServerRateLimitClient, parseCodexUsage } from "../src/providers/codexUsage.js";
import { providerById } from "../src/core/providers.js";
import { ProviderUsageService } from "../src/services/providerUsage.js";

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe("provider usage parsers", () => {
  it("parses Codex five-hour, weekly, and reset-credit limits", () => {
    const usage = parseCodexUsage(providerById("codex"), {
      rateLimits: {
        primary: { usedPercent: 28, windowDurationMins: 300, resetsAt: 1_783_670_295 },
        secondary: { usedPercent: 16, windowDurationMins: 10_080, resetsAt: 1_784_239_093 }
      },
      rateLimitResetCredits: { availableCount: 2 }
    });

    expect(usage.status).toBe("available");
    expect(usage.fiveHour).toEqual({ usedPercent: 28, resetsAtEpochSeconds: 1_783_670_295 });
    expect(usage.weekly?.usedPercent).toBe(16);
    expect(usage.resetCount).toBe(2);
  });

  it("prefers the Codex-specific bucket in multi-limit responses", () => {
    const usage = parseCodexUsage(providerById("codex"), {
      rateLimits: { primary: { usedPercent: 99, windowDurationMins: 300 } },
      rateLimitsByLimitId: {
        codex: {
          primary: { usedPercent: 21, windowDurationMins: 300 },
          secondary: { usedPercent: 34, windowDurationMins: 10_080 }
        }
      }
    });

    expect(usage.fiveHour?.usedPercent).toBe(21);
    expect(usage.weekly?.usedPercent).toBe(34);
  });

  it("parses Claude utilization and ISO reset timestamps", () => {
    const usage = parseClaudeUsage(providerById("claude-code"), JSON.stringify({
      five_hour: { utilization: 24.6, resets_at: "2026-07-17T08:30:00Z" },
      seven_day: { utilization: 41.2, resets_at: 1_784_277_000 }
    }));

    expect(usage.status).toBe("available");
    expect(usage.fiveHour?.usedPercent).toBe(25);
    expect(usage.fiveHour?.resetsAtEpochSeconds).toBe(Date.parse("2026-07-17T08:30:00Z") / 1_000);
    expect(usage.weekly?.usedPercent).toBe(41);
    expect(usage.weekly?.resetsAtEpochSeconds).toBe(1_784_277_000);
  });
});

describe("ClaudeUsageConfigurationResolver", () => {
  it("resolves credentials and a custom HTTPS endpoint from settings", async () => {
    const root = await temporaryDirectory();
    const config = path.join(root, ".claude");
    await mkdir(config, { recursive: true });
    await writeFile(path.join(config, "settings.json"), JSON.stringify({
      env: { ANTHROPIC_BASE_URL: "https://gateway.example/v1", ANTHROPIC_AUTH_TOKEN: "test-token" }
    }));

    await expect(new ClaudeUsageConfigurationResolver({ environment: {}, homeDirectory: root }).resolve()).resolves.toEqual({
      usageUrl: "https://gateway.example/api/oauth/usage",
      bearerToken: "test-token"
    });
  });

  it("rejects insecure non-loopback endpoints", async () => {
    const resolver = new ClaudeUsageConfigurationResolver({
      environment: { ANTHROPIC_BASE_URL: "http://gateway.example", CLAUDE_CODE_OAUTH_TOKEN: "test-token" }
    });
    await expect(resolver.resolve()).resolves.toBeUndefined();
  });

  it("does not send official stored credentials to a custom endpoint", async () => {
    const root = await temporaryDirectory();
    const config = path.join(root, ".claude");
    await mkdir(config, { recursive: true });
    await writeFile(path.join(config, "settings.json"), JSON.stringify({ env: { ANTHROPIC_BASE_URL: "https://gateway.example" } }));
    await writeFile(path.join(config, ".credentials.json"), JSON.stringify({ claudeAiOauth: { accessToken: "official-token" } }));

    await expect(new ClaudeUsageConfigurationResolver({ environment: {}, homeDirectory: root }).resolve()).resolves.toBeUndefined();
  });

  it("reads official credentials through secure storage", async () => {
    const root = await temporaryDirectory();
    let reads = 0;
    const resolver = new ClaudeUsageConfigurationResolver({
      environment: {},
      homeDirectory: root,
      secureCredentialsReader: {
        async read(_directory, custom) {
          expect(custom).toBe(false);
          reads += 1;
          return JSON.stringify({ claudeAiOauth: { accessToken: "keychain-token" } });
        }
      }
    });

    await expect(resolver.resolve()).resolves.toEqual({
      usageUrl: "https://api.anthropic.com/api/oauth/usage",
      bearerToken: "keychain-token"
    });
    expect(reads).toBe(1);
  });
});

describe("ProviderUsageService", () => {
  it("caches usage through its TTL and supports invalidation", async () => {
    let now = 1_000;
    let calls = 0;
    const service = new ProviderUsageService({
      codex: {
        async load(provider) {
          calls += 1;
          return {
            providerId: provider.id,
            providerName: provider.displayName,
            status: "available",
            fiveHour: { usedPercent: calls }
          };
        }
      }
    }, () => now, 100);

    expect((await service.load(providerById("codex"))).fiveHour?.usedPercent).toBe(1);
    now = 1_100;
    expect((await service.load(providerById("codex"))).fiveHour?.usedPercent).toBe(1);
    now = 1_101;
    expect((await service.load(providerById("codex"))).fiveHour?.usedPercent).toBe(2);
    service.invalidate("codex");
    expect((await service.load(providerById("codex"))).fiveHour?.usedPercent).toBe(3);
  });

  it("returns unsupported for Gemini", async () => {
    const usage = await new ProviderUsageService({}).load(providerById("gemini"));
    expect(usage.status).toBe("unavailable");
    expect(usage.message).toContain("not supported");
  });
});

describe.skipIf(process.platform === "win32")("CodexAppServerRateLimitClient", () => {
  it("completes the initialize and rate-limit JSON-RPC exchange", async () => {
    const root = await temporaryDirectory();
    const executable = path.join(root, "codex");
    await writeFile(executable, `#!/bin/sh
while IFS= read -r line
do
  case "$line" in
    *'"id":1'*) printf '%s\\n' '{"id":1,"result":{"userAgent":"test"}}' ;;
    *'account/rateLimits/read'*) printf '%s\\n' '{"id":2,"result":{"rateLimits":{"primary":{"usedPercent":12,"windowDurationMins":300}}}}' ;;
  esac
done
`);
    await chmod(executable, 0o755);

    const response = await new CodexAppServerRateLimitClient(2_000).read(executable);

    expect((response.rateLimits as { primary: { usedPercent: number } }).primary.usedPercent).toBe(12);
  });
});

async function temporaryDirectory(): Promise<string> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "agentdock-usage-"));
  temporaryDirectories.push(directory);
  return directory;
}
