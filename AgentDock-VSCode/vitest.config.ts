import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      include: [
        "src/core/**/*.ts",
        "src/providers/**/*.ts",
        "src/services/**/*.ts",
        "src/terminal/{activity,exitMarker,streamText,taskState}.ts"
      ]
    }
  }
});
