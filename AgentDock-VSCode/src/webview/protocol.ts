import type {
  ProviderId,
  ProviderUsageView,
  SessionCardView,
  SessionPreview
} from "../core/model.js";

export interface DashboardProviderView {
  id: ProviderId;
  displayName: string;
  enabled: boolean;
  iconUri: string;
}

export interface DashboardViewState {
  type: "dashboard-state";
  projectPath: string | null;
  loading: boolean;
  refreshing: boolean;
  error?: string;
  providers: DashboardProviderView[];
  sessions: SessionCardView[];
}

export type ExtensionToWebviewMessage =
  | DashboardViewState
  | { type: "session-preview"; sessionId: string; preview: SessionPreview }
  | { type: "provider-usage"; usage: ProviderUsageView }
  | { type: "action-error"; message: string };

export type WebviewToExtensionMessage =
  | { type: "ready" }
  | { type: "refresh" }
  | { type: "open-settings" }
  | { type: "toggle-pin"; sessionId: string }
  | { type: "open-session"; sessionId: string; yolo: boolean }
  | { type: "load-preview"; sessionId: string }
  | { type: "load-provider-usage"; providerId: ProviderId };
