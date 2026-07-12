import type { AgentSession, SessionMetrics } from "../core/model.js";

export interface MementoLike {
  get<T>(key: string, defaultValue: T): T;
  update(key: string, value: unknown): Thenable<void>;
}

export interface PersistedMetrics {
  metrics: SessionMetrics;
  sourceModifiedAt: number;
  sourceLength: number;
  anchorDate: string;
}

interface PersistedSession {
  session: AgentSession;
  metrics?: PersistedMetrics;
}

interface PersistedProjectState {
  version: 1;
  sessions: Record<string, PersistedSession>;
}

const STATE_KEY = "agentdock.projectState";

export class ProjectStateStore {
  private state: PersistedProjectState;

  constructor(private readonly memento: MementoLike) {
    this.state = sanitizeState(memento.get<unknown>(STATE_KEY, undefined));
  }

  sessions(projectPath: string): AgentSession[] {
    return Object.values(this.state.sessions)
      .map((entry) => entry.session)
      .filter((session) => session.projectPath === projectPath)
      .sort(sessionComparator);
  }

  async mergeDiscovered(projectPath: string, discovered: readonly AgentSession[]): Promise<AgentSession[]> {
    for (const candidate of discovered) {
      const existing = this.state.sessions[candidate.id];
      this.state.sessions[candidate.id] = {
        session: {
          ...candidate,
          pinned: existing?.session.pinned ?? candidate.pinned,
          createdAt: Math.min(positive(existing?.session.createdAt) ?? candidate.createdAt, candidate.createdAt),
          updatedAt: Math.max(existing?.session.updatedAt ?? 0, candidate.updatedAt)
        },
        metrics: existing?.metrics
      };
    }
    await this.persist();
    return this.sessions(projectPath);
  }

  async togglePin(sessionId: string): Promise<boolean | undefined> {
    const entry = this.state.sessions[sessionId];
    if (!entry) return undefined;
    entry.session.pinned = !entry.session.pinned;
    await this.persist();
    return entry.session.pinned;
  }

  metrics(sessionId: string): PersistedMetrics | undefined {
    return this.state.sessions[sessionId]?.metrics;
  }

  async saveMetrics(sessionId: string, metrics: PersistedMetrics): Promise<void> {
    await this.saveMetricsBatch([[sessionId, metrics]]);
  }

  async saveMetricsBatch(entries: readonly (readonly [string, PersistedMetrics])[]): Promise<void> {
    let changed = false;
    for (const [sessionId, metrics] of entries) {
      const entry = this.state.sessions[sessionId];
      if (!entry) continue;
      entry.metrics = metrics;
      changed = true;
    }
    if (changed) await this.persist();
  }

  async removeMissingProjects(activeProjectPaths: ReadonlySet<string>): Promise<void> {
    let changed = false;
    for (const [id, entry] of Object.entries(this.state.sessions)) {
      if (!activeProjectPaths.has(entry.session.projectPath)) {
        delete this.state.sessions[id];
        changed = true;
      }
    }
    if (changed) await this.persist();
  }

  private async persist(): Promise<void> {
    await this.memento.update(STATE_KEY, this.state);
  }
}

function sanitizeState(value: unknown): PersistedProjectState {
  if (!isRecord(value) || value.version !== 1 || !isRecord(value.sessions)) {
    return { version: 1, sessions: {} };
  }
  const sessions: Record<string, PersistedSession> = {};
  for (const [id, rawEntry] of Object.entries(value.sessions)) {
    if (!isRecord(rawEntry) || !isAgentSession(rawEntry.session) || rawEntry.session.id !== id) continue;
    sessions[id] = {
      session: rawEntry.session,
      metrics: isPersistedMetrics(rawEntry.metrics) ? rawEntry.metrics : undefined
    };
  }
  return { version: 1, sessions };
}

function isAgentSession(value: unknown): value is AgentSession {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === "string" &&
    typeof value.projectPath === "string" &&
    typeof value.name === "string" &&
    (value.providerId === "codex" || value.providerId === "claude-code" || value.providerId === "gemini") &&
    typeof value.cwd === "string" &&
    typeof value.providerSessionId === "string" &&
    typeof value.historyFilePath === "string" &&
    typeof value.summary === "string" &&
    typeof value.createdAt === "number" &&
    typeof value.updatedAt === "number" &&
    typeof value.pinned === "boolean"
  );
}

function isPersistedMetrics(value: unknown): value is PersistedMetrics {
  if (!isRecord(value) || !isRecord(value.metrics)) return false;
  const metrics = value.metrics;
  return (
    (typeof metrics.totalTokens === "number" || metrics.totalTokens === null) &&
    numberArray(metrics.dailyTokens, false) &&
    numberArray(metrics.dailyAverageResponseMillis, true) &&
    typeof value.sourceModifiedAt === "number" &&
    typeof value.sourceLength === "number" &&
    typeof value.anchorDate === "string"
  );
}

function numberArray(value: unknown, nullable: boolean): boolean {
  return Array.isArray(value) && value.length === 7 && value.every((item) => typeof item === "number" || (nullable && item === null));
}

function isRecord(value: unknown): value is Record<string, any> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function positive(value?: number): number | undefined {
  return value !== undefined && value > 0 ? value : undefined;
}

export const sessionComparator = (left: AgentSession, right: AgentSession): number =>
  Number(right.pinned) - Number(left.pinned) || right.updatedAt - left.updatedAt || right.createdAt - left.createdAt;
