import { chmod, mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { providerById } from "../src/core/providers.js";
import { ProviderDetectionService } from "../src/services/providerDetection.js";

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe.skipIf(process.platform === "win32")("ProviderDetectionService", () => {
  it("detects and validates a direct executable", async () => {
    const root = await temporaryDirectory();
    const executable = await script(path.join(root, "codex"), 0);
    const result = await new ProviderDetectionService().detect({ ...providerById("codex"), executable });

    expect(result).toEqual({ status: "available", executablePath: executable });
  });

  it("discovers an nvm executable even when PATH does not include it", async () => {
    const root = await temporaryDirectory();
    const executable = await script(path.join(root, ".nvm", "versions", "node", "v24.13.0", "bin", "codex"), 0);
    const service = new ProviderDetectionService({
      platform: "darwin",
      homeDirectory: root,
      pathEnvironment: "",
      environment: { PATH: "", SHELL: "/missing-shell" }
    });

    expect(await service.detect(providerById("codex"))).toEqual({ status: "available", executablePath: executable });
  });

  it("skips a broken PATH executable and selects a working fallback", async () => {
    const root = await temporaryDirectory();
    const brokenDirectory = path.join(root, "broken-bin");
    await script(path.join(brokenDirectory, "codex"), 1);
    const fallback = await script(path.join(root, ".local", "bin", "codex"), 0);
    const service = new ProviderDetectionService({
      platform: "darwin",
      homeDirectory: root,
      pathEnvironment: brokenDirectory,
      environment: { PATH: brokenDirectory, SHELL: "/missing-shell" }
    });

    expect(await service.detect(providerById("codex"))).toEqual({ status: "available", executablePath: fallback });
  });

  it("reports an executable that exists but fails its version probe", async () => {
    const root = await temporaryDirectory();
    const executable = await script(path.join(root, "codex"), 1);
    const result = await new ProviderDetectionService().detect({ ...providerById("codex"), executable });

    expect(result.status).toBe("missing");
  });

  it("honors a custom detect command while substituting the resolved executable", async () => {
    const root = await temporaryDirectory();
    const executable = path.join(root, "codex");
    await mkdir(path.dirname(executable), { recursive: true });
    await writeFile(executable, "#!/bin/sh\n[ \"$1\" = probe ]\n");
    await chmod(executable, 0o755);

    const result = await new ProviderDetectionService().detect({
      ...providerById("codex"),
      executable,
      detectCommand: "{{executable}} probe"
    });

    expect(result).toEqual({ status: "available", executablePath: executable });
  });
});

async function temporaryDirectory(): Promise<string> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "agentdock-detection-"));
  temporaryDirectories.push(directory);
  return directory;
}

async function script(filePath: string, exitCode: number): Promise<string> {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, `#!/bin/sh\nexit ${exitCode}\n`);
  await chmod(filePath, 0o755);
  return filePath;
}
