import * as os from "node:os";
import * as path from "node:path";
import { escapeShellArgument } from "../core/commandRenderer.js";

export function exitMarkerPath(token: string): string {
  return path.join(os.tmpdir(), "agentdock", `${token}.exit`);
}

export function wrapWithExitMarker(command: string, markerPath: string, platform: NodeJS.Platform): string {
  if (platform === "win32") return command;
  const escapedMarker = escapeShellArgument(markerPath, platform);
  return `(${command}); __agentdock_exit=$?; printf '%s\\n' "$__agentdock_exit" > ${escapedMarker}; unset __agentdock_exit`;
}
