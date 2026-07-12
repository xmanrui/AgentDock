import { describe, expect, it } from "vitest";
import { wrapWithExitMarker } from "../src/terminal/exitMarker.js";

describe("wrapWithExitMarker", () => {
  it("writes the Unix command exit code to a safely escaped marker", () => {
    expect(wrapWithExitMarker("codex resume abc", "/tmp/agentdock marker.exit", "darwin")).toBe(
      "(codex resume abc); __agentdock_exit=$?; printf '%s\\n' \"$__agentdock_exit\" > '/tmp/agentdock marker.exit'; unset __agentdock_exit"
    );
  });

  it("leaves Windows commands untouched", () => {
    expect(wrapWithExitMarker("codex resume abc", "C:\\Temp\\agentdock.exit", "win32")).toBe("codex resume abc");
  });
});
