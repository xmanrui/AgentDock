import { describe, expect, it } from "vitest";
import { terminalTabTitle } from "../src/terminal/title.js";

describe("terminalTabTitle", () => {
  it("keeps the original title while idle", () => {
    expect(terminalTabTitle("Review the release", "idle")).toBe("Review the release");
  });

  it("uses a native animated codicon while working", () => {
    expect(terminalTabTitle("Review the release", "working")).toBe("$(loading~spin) Review the release");
  });

  it("uses a green marker while ready for review", () => {
    expect(terminalTabTitle("Review the release", "ready")).toBe("\u{1F7E2} Review the release");
  });
});
