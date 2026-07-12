import { describe, expect, it } from "vitest";
import { reduceTerminalTaskState } from "../src/terminal/taskState.js";

describe("reduceTerminalTaskState", () => {
  it("moves from idle to working, ready, and back to idle when viewed", () => {
    expect(reduceTerminalTaskState("idle", "activity-started")).toBe("working");
    expect(reduceTerminalTaskState("working", "activity-completed")).toBe("ready");
    expect(reduceTerminalTaskState("ready", "viewed")).toBe("idle");
  });

  it("does not clear working state on a view event", () => {
    expect(reduceTerminalTaskState("working", "viewed")).toBe("working");
  });
});
