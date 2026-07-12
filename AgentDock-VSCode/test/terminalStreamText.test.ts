import { describe, expect, it } from "vitest";
import {
  sanitizeTerminalStreamText,
  TerminalOutputBuffer,
  TerminalStreamTextTracker
} from "../src/terminal/streamText.js";

describe("sanitizeTerminalStreamText", () => {
  it("keeps readable multilingual model output and strips decorations", () => {
    expect(sanitizeTerminalStreamText("⏺ 正在检查项目中的终端监听逻辑。")).toBe("正在检查项目中的终端监听逻辑。");
    expect(sanitizeTerminalStreamText("验证\uE000画\uE000面\uE000、音\uE000轨")).toBe("验证画面、音轨");
  });

  it("removes ANSI controls and ignores prompts and status chrome", () => {
    expect(sanitizeTerminalStreamText("\u001b[32m• Implemented the parser.\u001b[0m")).toBe("Implemented the parser.");
    expect(sanitizeTerminalStreamText("› Refactor the parser")).toBeUndefined();
    expect(sanitizeTerminalStreamText("Working (2s - esc to interrupt)")).toBeUndefined();
  });
});

describe("TerminalStreamTextTracker", () => {
  it("emits new model output and throttles growing lines", () => {
    const tracker = new TerminalStreamTextTracker(500, 5);
    tracker.update(["› Refactor"], false, 0);
    expect(tracker.update(["› Refactor", "• Building"], true, 100)).toBe("Building");
    expect(tracker.update(["› Refactor", "• Building the"], true, 200)).toBeUndefined();
    expect(tracker.update(["› Refactor", "• Building the project"], true, 650)).toBe("Building the project");
  });

  it("does not re-emit lines that merely moved due to scrolling", () => {
    const tracker = new TerminalStreamTextTracker(0);
    tracker.update(["First line", "Second line"], false, 0);
    expect(tracker.update(["Second line", "First line"], true, 1)).toBeUndefined();
  });
});

describe("TerminalOutputBuffer", () => {
  it("turns raw shell execution chunks into readable ticker text", () => {
    const buffer = new TerminalOutputBuffer();
    expect(buffer.accept("\u001b[31m• Inspecting", true, 100)).toBe("Inspecting");
    expect(buffer.accept(" the project\r", true, 700)).toBe("Inspecting the project");
  });
});
