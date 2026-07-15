package com.agentdock.model

data class CLIProvider(
    var id: String = "",
    var displayName: String = "",
    var executable: String = "",
    var detectCommand: String = "",
    var startCommandTemplate: String = "",
    var yoloStartCommandTemplate: String = "",
    var resumeCommandTemplate: String = "",
    var yoloResumeCommandTemplate: String = "",
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
                yoloStartCommandTemplate = "{{executable}} --dangerously-bypass-approvals-and-sandbox",
                resumeCommandTemplate = "{{executable}} resume {{providerSessionId?}}",
                yoloResumeCommandTemplate = "{{executable}} resume --dangerously-bypass-approvals-and-sandbox {{providerSessionId?}}",
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
                yoloStartCommandTemplate = "{{executable}} --ide --name {{sessionName}} --dangerously-skip-permissions",
                resumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}} --ide",
                yoloResumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}} --ide --dangerously-skip-permissions",
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
                yoloStartCommandTemplate = "{{executable}} --yolo",
                resumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}}",
                yoloResumeCommandTemplate = "{{executable}} --resume {{providerSessionId?}} --yolo",
                supportsSessionId = true,
                supportsImport = true,
                enabled = true
            )
        )
    }
}
