export type ProviderId = "codex" | "claude-code" | "gemini";

export interface CliProvider {
  id: ProviderId;
  configKey: "codex" | "claudeCode" | "gemini";
  displayName: string;
  executable: string;
  detectCommand: string;
  startCommandTemplate: string;
  resumeCommandTemplate: string;
  yoloResumeCommandTemplate: string;
  enabled: boolean;
}

export interface AgentSession {
  id: string;
  projectPath: string;
  name: string;
  providerId: ProviderId;
  cwd: string;
  providerSessionId: string;
  historyFilePath: string;
  summary: string;
  createdAt: number;
  updatedAt: number;
  pinned: boolean;
}

export type SessionRole = "user" | "assistant";

export interface SessionMessage {
  role: SessionRole;
  text: string;
}

export interface SessionPreview {
  messages: SessionMessage[];
  omittedMessageCount: number;
  notice?: string;
}

export interface SessionMetrics {
  totalTokens: number | null;
  dailyTokens: number[];
  dailyAverageResponseMillis: Array<number | null>;
}

export type TerminalTaskState = "idle" | "working" | "ready";

export interface SessionRuntimeState {
  terminalOpen: boolean;
  taskState: TerminalTaskState;
  liveText?: string;
}

export interface SessionCardView extends AgentSession, SessionRuntimeState, SessionMetrics {
  providerName: string;
  updatedLabel: string;
}

export interface ProviderUsageWindow {
  usedPercent: number;
  resetsAtEpochSeconds?: number;
}

export type ProviderUsageStatus = "available" | "unavailable" | "unauthenticated";

export interface ProviderUsageSnapshot {
  providerId: ProviderId;
  providerName: string;
  status: ProviderUsageStatus;
  fiveHour?: ProviderUsageWindow;
  weekly?: ProviderUsageWindow;
  resetCount?: number;
  message?: string;
}

export interface ProviderUsageView extends ProviderUsageSnapshot {
  projectTokenTotal: number | null;
}

export interface DashboardState {
  projectPath: string | null;
  sessions: SessionCardView[];
  providers: Array<Pick<CliProvider, "id" | "displayName" | "enabled">>;
  count: number;
  loading: boolean;
  error?: string;
}

export interface ProviderCommandContext {
  provider: CliProvider;
  session: AgentSession;
  projectPath: string;
  platform: NodeJS.Platform;
}
