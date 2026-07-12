import { createReadStream } from "node:fs";
import { readdir, stat } from "node:fs/promises";
import * as readline from "node:readline";
import * as path from "node:path";

export interface FileFingerprint {
  modifiedAt: number;
  size: number;
}

export async function fileFingerprint(filePath: string): Promise<FileFingerprint | undefined> {
  try {
    const value = await stat(filePath);
    return value.isFile() ? { modifiedAt: value.mtimeMs, size: value.size } : undefined;
  } catch {
    return undefined;
  }
}

export async function directoryExists(directoryPath: string): Promise<boolean> {
  try {
    return (await stat(directoryPath)).isDirectory();
  } catch {
    return false;
  }
}

export async function* readLines(filePath: string): AsyncGenerator<string> {
  const input = createReadStream(filePath, { encoding: "utf8" });
  const lines = readline.createInterface({ input, crlfDelay: Infinity });
  try {
    for await (const line of lines) {
      yield line;
    }
  } finally {
    lines.close();
    input.destroy();
  }
}

export async function walkFiles(
  root: string,
  predicate: (filePath: string) => boolean,
  maximumDepth = 6
): Promise<string[]> {
  const files: string[] = [];
  async function visit(directory: string, depth: number): Promise<void> {
    if (depth > maximumDepth) return;
    let entries;
    try {
      entries = await readdir(directory, { withFileTypes: true });
    } catch {
      return;
    }
    await Promise.all(
      entries.map(async (entry) => {
        const entryPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
          await visit(entryPath, depth + 1);
        } else if (entry.isFile() && predicate(entryPath)) {
          files.push(entryPath);
        }
      })
    );
  }
  await visit(root, 0);
  return files;
}

export function belongsToProject(candidatePath: string, projectPath: string): boolean {
  const relative = path.relative(path.resolve(projectPath), path.resolve(candidatePath));
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}
