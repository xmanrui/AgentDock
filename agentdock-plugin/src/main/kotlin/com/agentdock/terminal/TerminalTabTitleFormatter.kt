package com.agentdock.terminal

object TerminalTabTitleFormatter {
    private const val MAX_VISIBLE_CODE_POINTS = 5
    private const val ELLIPSIS = "..."
    private const val FALLBACK_TITLE = "Agent"

    fun fullTitle(raw: String?): String {
        return raw?.trim().orEmpty().ifBlank { FALLBACK_TITLE }
    }

    fun displayTitle(raw: String?): String {
        val title = fullTitle(raw)
        if (title.codePointCount(0, title.length) <= MAX_VISIBLE_CODE_POINTS) {
            return title
        }

        val endIndex = title.offsetByCodePoints(0, MAX_VISIBLE_CODE_POINTS)
        return title.substring(0, endIndex) + ELLIPSIS
    }
}
