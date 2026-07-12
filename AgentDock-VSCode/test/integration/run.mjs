import path from "node:path";
import { fileURLToPath } from "node:url";
import { runTests } from "@vscode/test-electron";

const directory = path.dirname(fileURLToPath(import.meta.url));
const extensionDevelopmentPath = path.resolve(directory, "../..");
const extensionTestsPath = path.join(extensionDevelopmentPath, "dist", "integration-tests.cjs");
const testWorkspace = path.join(directory, "workspace");

try {
  await runTests({
    extensionDevelopmentPath,
    extensionTestsPath,
    launchArgs: [
      testWorkspace,
      "--disable-extensions",
      "--force-disable-user-env",
      "--skip-welcome",
      "--skip-release-notes"
    ]
  });
} catch (error) {
  console.error("AgentDock VS Code integration tests failed", error);
  process.exitCode = 1;
}
