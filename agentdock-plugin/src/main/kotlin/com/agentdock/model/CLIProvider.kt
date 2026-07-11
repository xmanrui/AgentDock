package com.agentdock.model

data class CLIProvider(
    var id: String = "",
    var displayName: String = "",
    var executable: String = "",
    var detectCommand: String = "",
    var startCommandTemplate: String = "",
    var resumeCommandTemplate: String = "",
    var supportsSessionId: Boolean = true,
    var supportsImport: Boolean = false,
    var enabled: Boolean = true
) {
    companion object {
        const val CODEX_ID = "codex"
        const val CLAUDE_CODE_ID = "claude-code"
        const val GEMINI_ID = "gemini"

        fun defaultProviders(): List<CLIProvider> = listOf(
            CLIProvider(
                id = CODEX_ID,
                displayName = "Codex",
                executable = "codex",
                detectCommand = "codex --version",
                startCommandTemplate = "{{executable}}",
                resumeCommandTemplate = "{{executable}} resume {{providerSessionId?}}",
                supportsSessionId = true,
                supportsImport = false,
                enabled = true
            ),
            CLIProvider(
                id = CLAUDE_CODE_ID,
                displayName = "Claude Code",
                executable = "claude",
                detectCommand = "claude --version",
                startCommandTemplate = "{{executable}} --ide --name {{sessionName}}",
                resumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}} --ide",
                supportsSessionId = true,
                supportsImport = false,
                enabled = true
            ),
            CLIProvider(
                id = GEMINI_ID,
                displayName = "Gemini CLI",
                executable = "gemini",
                detectCommand = "gemini --version",
                startCommandTemplate = "{{executable}}",
                resumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}}",
                supportsSessionId = true,
                supportsImport = true,
                enabled = true
            )
        )
    }
}
