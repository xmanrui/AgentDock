import type { ProviderCommandContext } from "./model.js";

export type CommandRenderResult =
  | { ok: true; command: string }
  | { ok: false; missingVariable: string };

const VARIABLE_PATTERN = /\{\{([A-Za-z0-9_]+)(\?)?}}/g;

export function renderCommand(template: string, context: ProviderCommandContext): CommandRenderResult {
  const values: Record<string, string | undefined> = {
    executable: context.provider.executable,
    providerSessionId: context.session.providerSessionId,
    sessionName: context.session.name,
    cwd: context.session.cwd,
    projectPath: context.projectPath
  };
  let missingVariable: string | undefined;
  const command = template.replace(VARIABLE_PATTERN, (placeholder, variable: string, optionalMark: string) => {
    const rawValue = values[variable];
    if (!rawValue?.trim()) {
      if (optionalMark === "?") {
        return "";
      }
      missingVariable ??= variable;
      return placeholder;
    }
    return escapeShellArgument(rawValue, context.platform);
  });

  if (missingVariable) {
    return { ok: false, missingVariable };
  }
  return { ok: true, command: command.trim() };
}

export function escapeShellArgument(value: string, platform: NodeJS.Platform): string {
  if (value.length === 0) {
    return platform === "win32" ? '""' : "''";
  }
  if (/^[A-Za-z0-9_./:=@%+-]+$/.test(value)) {
    return value;
  }
  if (platform === "win32") {
    return `"${value.replace(/(["^%])/g, "^$1")}"`;
  }
  return `'${value.replace(/'/g, `'"'"'`)}'`;
}
