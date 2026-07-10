# Publishing AgentDock

This checklist covers the free, open-source JetBrains Marketplace release of AgentDock.

## Marketplace Listing

Use these values for the first upload:

| Field | Value |
| --- | --- |
| Plugin name | AgentDock |
| Plugin ID | `com.agentdock` |
| Vendor | AgentDock |
| Vendor email | `longmanr307@gmail.com` |
| Website | `https://github.com/xmanrui/AgentDock` |
| Source code | `https://github.com/xmanrui/AgentDock` |
| Issue tracker | `https://github.com/xmanrui/AgentDock/issues` |
| Privacy policy | `https://github.com/xmanrui/AgentDock/blob/main/PRIVACY.md` |
| License | MIT |
| Minimum IDE build | `252` / JetBrains IDE 2025.2 |
| Release channel | Stable/default |

The plugin is free. Do not add a paid-plugin `<product-descriptor>` or Marketplace licensing checks.

## Verify a Release

Use JDK 21 and run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :agentdock-plugin:test \
  :agentdock-plugin:verifyPluginStructure \
  :agentdock-plugin:verifyPluginProjectConfiguration \
  :agentdock-plugin:verifyPlugin \
  :agentdock-plugin:buildPlugin
```

Install the generated ZIP from `agentdock-plugin/build/distributions/` into a clean target IDE and exercise session discovery, start/resume commands, provider settings, usage information, restart, and uninstall/reinstall behavior.

## Signing Secrets

The Gradle build reads signing file locations and the key password only from environment variables:

- `CERTIFICATE_CHAIN_FILE`
- `PRIVATE_KEY_FILE`
- `PRIVATE_KEY_PASSWORD`

It reads the Marketplace token from `PUBLISH_TOKEN`. Never commit any of these values or the referenced private key to the repository. For local releases, keep signing files outside the repository. In CI, restore certificate and key files from protected secrets before invoking Gradle.

With the signing variables configured, create the signed archive with:

```bash
./gradlew :agentdock-plugin:signPlugin
```

## First Marketplace Upload

The first release must be uploaded manually:

1. Sign in to JetBrains Marketplace.
2. Accept the Marketplace Developer Agreement and create/select the AgentDock Vendor profile.
3. Upload the signed ZIP from `agentdock-plugin/build/distributions/`.
4. Select the MIT license and provide the source code URL.
5. Add relevant tags, screenshots, the issue tracker, and privacy policy.
6. Submit the plugin for review.

## Later Releases

Before publishing an update:

1. Increase `version` in `agentdock-plugin/build.gradle.kts`.
2. Update `changeNotes` with user-visible changes.
3. Run the full verification command above.
4. Publish with protected environment variables configured:

```bash
./gradlew :agentdock-plugin:publishPlugin
```

Every uploaded update is reviewed before it becomes publicly available.

## Official Documentation

- [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
- [Uploading a New Plugin](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html)
- [Marketplace Approval Guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)
