import { spawn } from "node:child_process";
import { constants } from "node:fs";
import { access, readdir, stat } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { escapeShellArgument } from "../core/commandRenderer.js";
import type { CliProvider } from "../core/model.js";

export type ProviderDetectionResult =
  | { status: "available"; executablePath: string }
  | { status: "missing"; reason: string }
  | { status: "disabled"; reason: string };

interface DetectionOptions {
  platform?: NodeJS.Platform;
  homeDirectory?: string;
  pathEnvironment?: string;
  environment?: NodeJS.ProcessEnv;
}

export class ProviderDetectionService {
  private readonly platform: NodeJS.Platform;
  private readonly homeDirectory: string;
  private readonly pathEnvironment: string;
  private readonly environment: NodeJS.ProcessEnv;

  constructor(options: DetectionOptions = {}) {
    this.platform = options.platform ?? process.platform;
    this.homeDirectory = options.homeDirectory ?? os.homedir();
    this.environment = options.environment ?? process.env;
    this.pathEnvironment = options.pathEnvironment ?? this.environment.PATH ?? "";
  }

  async detect(provider: CliProvider): Promise<ProviderDetectionResult> {
    if (!provider.enabled) return { status: "disabled", reason: "Provider is disabled" };
    const executable = provider.executable.trim();
    if (!executable) return { status: "missing", reason: `${provider.displayName} executable is empty` };

    if (executable.includes("/") || executable.includes("\\")) {
      const direct = path.resolve(executable);
      return (await this.isUsableExecutable(direct, provider))
        ? { status: "available", executablePath: direct }
        : { status: "missing", reason: `Executable is not available or failed to start: ${executable}` };
    }

    let candidateFound = false;
    const candidates = await this.executableCandidates(executable);
    const seen = new Set<string>();
    for (const candidate of candidates) {
      if (seen.has(candidate)) continue;
      seen.add(candidate);
      if (!(await isExecutableFile(candidate, this.platform))) continue;
      candidateFound = true;
      if (await this.isUsableExecutable(candidate, provider)) {
        return { status: "available", executablePath: candidate };
      }
    }
    return candidateFound
      ? { status: "missing", reason: `Executable candidates were found but failed to start: ${executable}` }
      : { status: "missing", reason: `Executable not found in VS Code PATH or login shell: ${executable}` };
  }

  private async executableCandidates(executable: string): Promise<string[]> {
    return [
      ...this.pathCandidates(executable),
      ...this.commonCandidates(executable),
      ...(await this.versionManagerCandidates(executable)),
      ...this.applicationBundleCandidates(executable),
      ...(await this.loginShellCandidates(executable))
    ];
  }

  private pathCandidates(executable: string): string[] {
    const names = this.platform === "win32"
      ? [executable, ...(this.environment.PATHEXT ?? ".EXE;.BAT;.CMD").split(";").map((suffix) => `${executable}${suffix.toLowerCase()}`)]
      : [executable];
    return this.pathEnvironment
      .split(path.delimiter)
      .filter(Boolean)
      .flatMap((directory) => names.map((name) => path.resolve(directory, name)));
  }

  private commonCandidates(executable: string): string[] {
    return [
      path.join(this.homeDirectory, ".local", "bin"),
      path.join(this.homeDirectory, "bin"),
      path.join(this.homeDirectory, ".bun", "bin"),
      "/opt/homebrew/bin",
      "/opt/homebrew/sbin",
      "/usr/local/bin",
      "/usr/bin",
      "/bin"
    ].map((directory) => path.join(directory, executable));
  }

  private async versionManagerCandidates(executable: string): Promise<string[]> {
    const direct = [
      path.join(this.homeDirectory, ".volta", "bin", executable),
      path.join(this.homeDirectory, ".asdf", "shims", executable),
      path.join(this.homeDirectory, ".local", "share", "mise", "shims", executable),
      path.join(this.homeDirectory, ".npm-global", "bin", executable),
      path.join(this.homeDirectory, "Library", "pnpm", executable)
    ];
    const roots = [
      [path.join(this.homeDirectory, ".nvm", "versions", "node"), path.join("bin", executable)],
      [path.join(this.homeDirectory, ".fnm", "node-versions"), path.join("installation", "bin", executable)],
      [path.join(this.homeDirectory, ".local", "share", "fnm", "node-versions"), path.join("installation", "bin", executable)]
    ] as const;
    const versioned: Array<{ file: string; score: number; modifiedAt: number }> = [];
    for (const [root, relativeExecutable] of roots) {
      let entries;
      try {
        entries = await readdir(root, { withFileTypes: true });
      } catch {
        continue;
      }
      for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const directory = path.join(root, entry.name);
        const details = await stat(directory).catch(() => undefined);
        versioned.push({
          file: path.join(directory, relativeExecutable),
          score: versionScore(entry.name),
          modifiedAt: details?.mtimeMs ?? 0
        });
      }
    }
    versioned.sort((left, right) => right.score - left.score || right.modifiedAt - left.modifiedAt);
    return [...direct, ...versioned.map((candidate) => candidate.file)];
  }

  private applicationBundleCandidates(executable: string): string[] {
    if (this.platform !== "darwin" || executable !== "codex") return [];
    return [
      "/Applications/Codex.app/Contents/Resources/codex",
      "/Applications/ChatGPT.app/Contents/Resources/codex",
      path.join(this.homeDirectory, "Applications", "Codex.app", "Contents", "Resources", "codex"),
      path.join(this.homeDirectory, "Applications", "ChatGPT.app", "Contents", "Resources", "codex")
    ];
  }

  private async loginShellCandidates(executable: string): Promise<string[]> {
    if (this.platform === "win32") return [];
    const shell = this.environment.SHELL || (this.platform === "darwin" ? "/bin/zsh" : "/bin/sh");
    if (!(await isExecutableFile(shell, this.platform))) return [];
    const escaped = escapeShellArgument(executable, this.platform);
    const output: string[] = [];
    for (const flag of ["-lc", "-lic"]) {
      const result = await runProcess(shell, [flag, `command -v ${escaped}`], 3_000, this.environment, true);
      if (result.exitCode !== 0) continue;
      output.push(...result.stdout.split(/\r?\n/).map((line) => line.trim()).filter((line) => path.isAbsolute(line)));
    }
    return output;
  }

  private async isUsableExecutable(executable: string, provider: CliProvider): Promise<boolean> {
    if (!(await isExecutableFile(executable, this.platform))) return false;
    const directory = path.dirname(executable);
    const environment = {
      ...this.environment,
      PATH: `${directory}${path.delimiter}${this.pathEnvironment}`
    };
    const detectCommand = provider.detectCommand.trim();
    const remainder = commandRemainder(detectCommand);
    const result = !detectCommand || remainder === "--version"
      ? await runProcess(executable, ["--version"], 3_000, environment, true, this.platform === "win32")
      : await this.runDetectCommand(renderDetectCommand(detectCommand, executable, this.platform), environment);
    return result.exitCode === 0;
  }

  private async runDetectCommand(command: string, environment: NodeJS.ProcessEnv): Promise<ProcessResult> {
    if (this.platform === "win32") {
      return runProcess(environment.ComSpec ?? "cmd.exe", ["/d", "/s", "/c", command], 3_000, environment, true);
    }
    const shell = this.environment.SHELL || (this.platform === "darwin" ? "/bin/zsh" : "/bin/sh");
    return runProcess(shell, ["-c", command], 3_000, environment, true);
  }
}

interface ProcessResult {
  exitCode: number | null;
  stdout: string;
}

export function runProcess(
  executable: string,
  args: readonly string[],
  timeoutMillis: number,
  environment: NodeJS.ProcessEnv = process.env,
  discardStderr = false,
  shell = false
): Promise<ProcessResult> {
  return new Promise((resolve) => {
    let stdout = "";
    let settled = false;
    const child = spawn(executable, args, {
      env: environment,
      shell,
      windowsHide: true,
      stdio: ["ignore", "pipe", discardStderr ? "ignore" : "pipe"]
    });
    child.stdout?.setEncoding("utf8");
    child.stdout?.on("data", (chunk: string) => {
      if (stdout.length < 64 * 1024) stdout += chunk;
    });
    child.stderr?.resume();
    const finish = (exitCode: number | null): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve({ exitCode, stdout });
    };
    const timer = setTimeout(() => {
      child.kill("SIGKILL");
      finish(null);
    }, timeoutMillis);
    child.once("error", () => finish(null));
    child.once("exit", (code) => finish(code));
  });
}

async function isExecutableFile(filePath: string, platform: NodeJS.Platform): Promise<boolean> {
  try {
    const details = await stat(filePath);
    if (!details.isFile()) return false;
    if (platform !== "win32") await access(filePath, constants.X_OK);
    return true;
  } catch {
    return false;
  }
}

function versionScore(name: string): number {
  const values = [...name.matchAll(/\d+/g)].slice(0, 3).map((match) => Number(match[0]));
  return (values[0] ?? 0) * 1_000_000_000 + (values[1] ?? 0) * 1_000_000 + (values[2] ?? 0);
}

function commandRemainder(command: string): string | undefined {
  const firstToken = /^\s*(?:"[^"]*"|'[^']*'|\S+)/.exec(command)?.[0];
  return firstToken ? command.slice(firstToken.length).trim() : undefined;
}

function renderDetectCommand(command: string, executable: string, platform: NodeJS.Platform): string {
  const escaped = escapeShellArgument(executable, platform);
  if (command.includes("{{executable}}")) return command.replaceAll("{{executable}}", escaped);
  const firstToken = /^\s*(?:"[^"]*"|'[^']*'|\S+)/.exec(command)?.[0];
  return firstToken ? `${escaped}${command.slice(firstToken.length)}` : `${escaped} --version`;
}
