package com.agentdock.terminal

import com.agentdock.model.ProviderCommandContext

sealed class CommandRenderResult {
    data class Success(val command: String) : CommandRenderResult()
    data class MissingVariable(val variable: String) : CommandRenderResult()
}

class CommandRenderer(private val shellEscaper: ShellEscaper = ShellEscaper()) {
    private val variablePattern = Regex("\\{\\{([A-Za-z0-9_]+)(\\?)?}}")

    fun render(template: String, context: ProviderCommandContext): CommandRenderResult {
        val values = mapOf(
            "executable" to context.provider.executable,
            "providerSessionId" to context.session.providerSessionId,
            "sessionName" to context.session.name,
            "cwd" to context.session.cwd,
            "projectPath" to context.projectPath
        )

        var rendered = template
        for (match in variablePattern.findAll(template).toList()) {
            val variable = match.groupValues[1]
            val optional = match.groupValues[2] == "?"
            val rawValue = values[variable]
            if (rawValue.isNullOrBlank()) {
                if (optional) {
                    rendered = rendered.replace(match.value, "")
                    continue
                }
                return CommandRenderResult.MissingVariable(variable)
            }
            rendered = rendered.replace(match.value, shellEscaper.escape(rawValue, context.os))
        }

        return CommandRenderResult.Success(rendered.trim())
    }
}
