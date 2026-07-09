package com.agentdock.util

object SessionTextSanitizer {
    fun title(raw: String?, fallback: String = "Untitled agent session"): String {
        return summary(raw)
            .take(64)
            .trim()
            .ifBlank { fallback }
    }

    fun summary(raw: String?): String {
        return clean(raw)
            .firstUsefulSentence()
            .orEmpty()
            .take(180)
            .trim()
    }

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("<environment_context[\\s\\S]*?</environment_context>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<codex_internal_context[\\s\\S]*?</codex_internal_context>", RegexOption.IGNORE_CASE), "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.isNoisyContextLine() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isNoisy(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        val lower = raw.lowercase()
        return lower.contains("<environment_context") ||
            lower.contains("<cwd>") ||
            lower.contains("<shell>") ||
            raw.lineSequence().any { it.trim().isNoisyContextLine() }
    }

    private fun String.firstUsefulSentence(): String? {
        if (isBlank()) return null
        return Regex("^(.+?[。！？.!?])(?:\\s|$)")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: this
    }

    private fun String.isNoisyContextLine(): Boolean {
        val lower = lowercase()
        return lower.startsWith("<") ||
            lower.startsWith("cwd") ||
            lower.startsWith("shell") ||
            lower.startsWith("context") ||
            lower.startsWith("current date") ||
            lower.startsWith("filesystem") ||
            lower.startsWith("approval") ||
            lower.startsWith("sandbox") ||
            lower.startsWith("model:") ||
            lower.startsWith("model_provider") ||
            lower.startsWith("working directory") ||
            lower.startsWith("imported from codex local history") ||
            lower.startsWith("imported from claude code local history")
    }
}
