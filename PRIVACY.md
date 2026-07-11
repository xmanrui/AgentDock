# AgentDock Privacy Policy

Last updated: July 11, 2026

AgentDock is a local-first, open-source plugin for JetBrains IDEs. This policy explains what data AgentDock accesses, how that data is used, and when network requests may occur.

## Data AgentDock Accesses

AgentDock may access the following data on your device to provide its features:

- the current project path and working directories;
- AgentDock provider settings and project-scoped session metadata;
- local Codex session files under `~/.codex`, including session indexes and session content used for discovery and previews;
- local Claude Code session files under `~/.claude/projects`, including session content used for discovery and previews;
- local Gemini CLI session files under `~/.gemini/tmp`, including project-scoped session content used for discovery, previews, and local usage metrics;
- configured provider executable paths and executable version information;
- provider account usage information, such as rate-limit utilization and reset times.

AgentDock reads local session content only to discover sessions and display previews inside the IDE. It does not create a separate persistent copy of complete session transcripts.

## Credential Handling

For Claude Code usage information, AgentDock may read an OAuth or authentication token from environment variables, Claude Code settings, Claude Code's local credentials file, or the macOS Keychain. The token is held in memory only for the request and is not saved to AgentDock settings or logs.

When the official Anthropic endpoint is used, locally stored Claude Code credentials are sent only to the official Anthropic usage endpoint. If you explicitly configure a custom `ANTHROPIC_BASE_URL` together with a token, that token is sent to the configured endpoint. You are responsible for trusting custom endpoints you configure.

For Codex usage information, AgentDock starts the locally installed Codex CLI app server and asks it for account rate-limit information. AgentDock does not directly read or store a Codex API key.

## Network Requests

AgentDock does not operate an AgentDock server and does not upload data to an AgentDock-controlled service.

Network activity may still occur in these cases:

- AgentDock requests Claude Code usage information from the configured Anthropic-compatible endpoint, which defaults to `https://api.anthropic.com/api/oauth/usage`.
- The locally installed Codex, Claude Code, or Gemini CLI may connect to its provider when AgentDock launches start or resume commands.
- The Codex CLI may connect to OpenAI services while responding to an account usage request.

Those third-party services process data under their own terms and privacy policies.

## Analytics and Telemetry

AgentDock does not collect analytics, advertising identifiers, crash reports, or telemetry, and does not sell personal data.

## Storage and Retention

- Provider settings are stored locally in the JetBrains IDE application configuration.
- Project session metadata is stored locally in the IDE workspace state for that project.
- Provider usage responses are cached in memory for a short period and are not persisted by AgentDock.
- Complete Terminal output is not stored by AgentDock.

Local settings remain until you reset or remove them, remove the relevant IDE configuration, or uninstall the plugin. Session data owned by Codex CLI, Claude Code CLI, or Gemini CLI remains governed by those tools and is not deleted when AgentDock is uninstalled.

## Your Choices

You can disable a provider or change its executable and command templates in **Tools > AgentDock Settings**. You can also stop AgentDock from accessing provider data by disabling the provider, removing the plugin, or revoking the corresponding provider credentials.

## Security

AgentDock limits credential use to the provider operations described above and does not intentionally write credentials to logs or persistent AgentDock storage. No software can guarantee absolute security, so keep your IDE, plugin, and provider CLIs up to date and configure only endpoints you trust.

## Changes to This Policy

This policy may be updated when AgentDock's data practices or features change. Material changes will be documented in the repository and plugin release notes.

## Contact

For privacy questions, contact [longmanr307@gmail.com](mailto:longmanr307@gmail.com) or open an issue at [github.com/xmanrui/AgentDock/issues](https://github.com/xmanrui/AgentDock/issues).
