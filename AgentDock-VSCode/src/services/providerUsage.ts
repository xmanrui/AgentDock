import type { CliProvider, ProviderId, ProviderUsageSnapshot } from "../core/model.js";
import { ClaudeUsageSource } from "../providers/claudeUsage.js";
import { CodexUsageSource } from "../providers/codexUsage.js";

export interface ProviderUsageSource {
  load(provider: CliProvider): Promise<ProviderUsageSnapshot>;
}

interface CacheEntry {
  snapshot: ProviderUsageSnapshot;
  loadedAt: number;
}

export class ProviderUsageService {
  private readonly cache = new Map<ProviderId, CacheEntry>();
  private readonly pending = new Map<ProviderId, Promise<ProviderUsageSnapshot>>();

  constructor(
    private readonly sources: Partial<Record<ProviderId, ProviderUsageSource>> = {
      codex: new CodexUsageSource(),
      "claude-code": new ClaudeUsageSource()
    },
    private readonly now = () => Date.now(),
    private readonly cacheTtlMillis = 120_000
  ) {}

  async load(provider: CliProvider): Promise<ProviderUsageSnapshot> {
    const cached = this.cache.get(provider.id);
    if (cached && this.now() - cached.loadedAt <= this.cacheTtlMillis) return cached.snapshot;
    if (cached) this.cache.delete(provider.id);
    const existing = this.pending.get(provider.id);
    if (existing) return existing;

    const request = this.loadFresh(provider).finally(() => this.pending.delete(provider.id));
    this.pending.set(provider.id, request);
    return request;
  }

  invalidate(providerId?: ProviderId): void {
    if (providerId) this.cache.delete(providerId);
    else this.cache.clear();
  }

  private async loadFresh(provider: CliProvider): Promise<ProviderUsageSnapshot> {
    const source = this.sources[provider.id];
    let snapshot: ProviderUsageSnapshot;
    if (!source) {
      snapshot = {
        providerId: provider.id,
        providerName: provider.displayName,
        status: "unavailable",
        message: "Usage limits are not supported for this provider."
      };
    } else {
      try {
        snapshot = await source.load(provider);
      } catch {
        snapshot = {
          providerId: provider.id,
          providerName: provider.displayName,
          status: "unavailable",
          message: "Could not load usage limits right now."
        };
      }
    }
    this.cache.set(provider.id, { snapshot, loadedAt: this.now() });
    return snapshot;
  }
}
