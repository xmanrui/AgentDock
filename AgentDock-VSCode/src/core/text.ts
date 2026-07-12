const ENVIRONMENT_BLOCK = /<environment_context[\s\S]*?<\/environment_context>/gi;
const INTERNAL_CONTEXT_BLOCK = /<codex_internal_context[\s\S]*?<\/codex_internal_context>/gi;
const FIRST_SENTENCE = /^(.+?[。！？.!?])(?:\s|$)/;

export function cleanSessionText(raw?: string | null): string {
  if (!raw?.trim()) {
    return "";
  }
  return raw
    .replace(ENVIRONMENT_BLOCK, "\n")
    .replace(INTERNAL_CONTEXT_BLOCK, "\n")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !isNoisyContextLine(line))
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

export function sessionSummary(raw?: string | null): string {
  const cleaned = cleanSessionText(raw);
  const sentence = FIRST_SENTENCE.exec(cleaned)?.[1] ?? cleaned;
  return sentence.slice(0, 180).trim();
}

export function sessionTitle(raw?: string | null, fallback = "Untitled agent session"): string {
  return sessionSummary(raw).slice(0, 64).trim() || fallback;
}

export function sanitizePreviewMessage(raw?: string | null, maximumLength = 4_000): string {
  if (!raw?.trim()) {
    return "";
  }
  const cleaned = raw
    .replace(ENVIRONMENT_BLOCK, "\n")
    .replace(INTERNAL_CONTEXT_BLOCK, "\n")
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
  return cleaned.length <= maximumLength ? cleaned : `${cleaned.slice(0, maximumLength - 3).trimEnd()}...`;
}

export function formatRelativeTime(timestamp: number, now = Date.now(), locale = "zh-CN"): string {
  if (timestamp <= 0) {
    return "-";
  }
  const seconds = Math.max(0, Math.floor((now - timestamp) / 1_000));
  if (locale.startsWith("zh")) {
    if (seconds < 60) return "刚刚";
    if (seconds < 3_600) return `${Math.floor(seconds / 60)} 分钟前`;
    if (seconds < 86_400) return `${Math.floor(seconds / 3_600)} 小时前`;
    if (seconds < 604_800) return `${Math.floor(seconds / 86_400)} 天前`;
  } else {
    if (seconds < 60) return "just now";
    if (seconds < 3_600) return `${Math.floor(seconds / 60)} min ago`;
    if (seconds < 86_400) return `${Math.floor(seconds / 3_600)} hr ago`;
    if (seconds < 604_800) return `${Math.floor(seconds / 86_400)} days ago`;
  }
  return new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(timestamp));
}

function isNoisyContextLine(line: string): boolean {
  const lower = line.toLowerCase();
  return (
    lower.startsWith("<") ||
    lower.startsWith("cwd") ||
    lower.startsWith("shell") ||
    lower.startsWith("context") ||
    lower.startsWith("current date") ||
    lower.startsWith("filesystem") ||
    lower.startsWith("approval") ||
    lower.startsWith("sandbox") ||
    lower.startsWith("model:") ||
    lower.startsWith("model_provider") ||
    lower.startsWith("working directory") ||
    lower.startsWith("imported from codex local history") ||
    lower.startsWith("imported from claude code local history")
  );
}
