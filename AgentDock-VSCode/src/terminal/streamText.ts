/* eslint-disable no-control-regex -- Terminal output filtering intentionally matches ANSI/control bytes. */
const ANSI_OSC = new RegExp("\\u001b\\][^\\u0007]*(?:\\u0007|\\u001b\\\\)", "g");
const ANSI_CSI = new RegExp(
  "[\\u001b\\u009b][[\\]()#;?]*(?:(?:(?:[a-zA-Z\\d]*(?:;[-a-zA-Z\\d/#&.:=?%@~_]+)*)?\\u0007)|(?:(?:\\d{1,4}(?:[;:]\\d{0,4})*)?[\\dA-PR-TZcf-nq-uy=><~]))",
  "g"
);
const CONTROL_CHARACTERS = new RegExp("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]", "g");
const PROMPT_PREFIX = /^(?:\([^)]*\)\s*)?(?:[>$#]|[›❯➜])(?:\s|$)/;
const LEADING_DECORATION = /^[\s*•⏺✦✧✻✽●○◆◇└├│]+/;
const STATUS_LINE = /^(?:thinking|working|loading|generating|responding)(?:\s|[.·(]|$)/i;
const IGNORED_FRAGMENTS = [
  "esc to interrupt",
  "ctrl+c to",
  "? for shortcuts",
  "shift+tab",
  "tokens left",
  "context left",
  "bypass permissions",
  "auto-accept",
  "press enter to",
  "type your message"
];

export function sanitizeTerminalStreamText(raw: string): string | undefined {
  let text = stripAnsi(raw)
    .split("\uE000").join("")
    .replace(CONTROL_CHARACTERS, " ")
    .replace(/\t/g, " ")
    .trim();
  if (!text || PROMPT_PREFIX.test(text)) return undefined;
  const lowercase = text.toLocaleLowerCase();
  if (IGNORED_FRAGMENTS.some((fragment) => lowercase.includes(fragment))) return undefined;
  text = text.replace(LEADING_DECORATION, "").replace(/\s+/g, " ").trim();
  if (text.length < 3 || !/[\p{L}\p{N}]/u.test(text) || STATUS_LINE.test(text)) return undefined;
  return text.length <= 96 ? text : `${text.slice(0, 93).trimEnd()}...`;
}

export class TerminalStreamTextTracker {
  private previousLines: string[] = [];
  private pendingText?: string;
  private lastEmittedText?: string;
  private lastEmittedAt?: number;
  private wasWorking = false;

  constructor(private readonly emitIntervalMillis = 550, private readonly minimumGrowthCharacters = 8) {}

  update(lines: readonly string[], working: boolean, nowMillis: number): string | undefined {
    const normalized = lines.map(sanitizeTerminalStreamText).filter((value): value is string => Boolean(value));
    if (!working) {
      this.previousLines = normalized;
      this.pendingText = undefined;
      this.lastEmittedText = undefined;
      this.lastEmittedAt = undefined;
      this.wasWorking = false;
      return undefined;
    }
    if (!this.wasWorking) {
      this.pendingText = undefined;
      this.lastEmittedText = undefined;
      this.lastEmittedAt = undefined;
      this.wasWorking = true;
    }
    const previous = new Set(this.previousLines);
    const candidate = [...normalized].reverse().find((line) => !previous.has(line));
    if (candidate) this.pendingText = candidate;
    this.previousLines = normalized;

    const pending = this.pendingText;
    if (!pending) return undefined;
    if (this.lastEmittedAt !== undefined && nowMillis - this.lastEmittedAt < this.emitIntervalMillis) return undefined;
    if (!this.hasEnoughNewContent(pending)) return undefined;
    this.pendingText = undefined;
    this.lastEmittedText = pending;
    this.lastEmittedAt = nowMillis;
    return pending;
  }

  private hasEnoughNewContent(candidate: string): boolean {
    const previous = this.lastEmittedText;
    if (!previous) return true;
    if (candidate === previous) return false;
    return !candidate.startsWith(previous) || candidate.length - previous.length >= this.minimumGrowthCharacters;
  }
}

export class TerminalOutputBuffer {
  private currentLine = "";
  private readonly lines: string[] = [];
  private readonly tracker = new TerminalStreamTextTracker();

  accept(chunk: string, working: boolean, nowMillis = Date.now()): string | undefined {
    const normalized = stripAnsi(chunk).replace(/\r(?!\n)/g, "\n");
    const parts = normalized.split("\n");
    this.currentLine += parts.shift() ?? "";
    for (const part of parts) {
      this.pushLine(this.currentLine);
      this.currentLine = part;
    }
    const snapshot = [...this.lines, this.currentLine].slice(-30);
    return this.tracker.update(snapshot, working, nowMillis);
  }

  reset(): void {
    this.currentLine = "";
    this.lines.length = 0;
    this.tracker.update([], false, Date.now());
  }

  private pushLine(line: string): void {
    if (line) this.lines.push(line);
    if (this.lines.length > 30) this.lines.splice(0, this.lines.length - 30);
  }
}

function stripAnsi(value: string): string {
  return value.replace(ANSI_OSC, "").replace(ANSI_CSI, "");
}
