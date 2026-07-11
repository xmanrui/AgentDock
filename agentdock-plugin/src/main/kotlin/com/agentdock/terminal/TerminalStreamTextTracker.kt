package com.agentdock.terminal

import com.jediterm.terminal.util.CharUtils

internal class TerminalStreamTextTracker(
    private val emitIntervalMs: Long = DEFAULT_EMIT_INTERVAL_MS,
    private val minimumGrowthCharacters: Int = DEFAULT_MINIMUM_GROWTH_CHARACTERS
) {
    private var previousLines = emptyList<String>()
    private var pendingText: String? = null
    private var lastEmittedText: String? = null
    private var lastEmittedAt: Long? = null
    private var wasWorking = false

    fun update(lines: List<String>, working: Boolean, nowMs: Long): String? {
        val normalizedLines = lines.mapNotNull(TerminalStreamTextSanitizer::sanitize)
        if (!working) {
            previousLines = normalizedLines
            pendingText = null
            lastEmittedText = null
            lastEmittedAt = null
            wasWorking = false
            return null
        }

        if (!wasWorking) {
            pendingText = null
            lastEmittedText = null
            lastEmittedAt = null
            wasWorking = true
        }

        val previousLineSet = previousLines.toHashSet()
        normalizedLines.asReversed()
            .firstOrNull { candidate -> candidate !in previousLineSet }
            ?.let { candidate -> pendingText = candidate }
        previousLines = normalizedLines

        val pending = pendingText ?: return null
        val previousEmission = lastEmittedAt
        if (previousEmission != null && nowMs - previousEmission < emitIntervalMs) return null
        if (!hasEnoughNewContent(pending)) return null

        pendingText = null
        lastEmittedText = pending
        lastEmittedAt = nowMs
        return pending
    }

    fun hasPendingText(): Boolean = pendingText != null

    private fun hasEnoughNewContent(candidate: String): Boolean {
        val previous = lastEmittedText ?: return true
        if (candidate == previous) return false
        if (!candidate.startsWith(previous)) return true
        return candidate.length - previous.length >= minimumGrowthCharacters
    }

    companion object {
        private const val DEFAULT_EMIT_INTERVAL_MS = 550L
        private const val DEFAULT_MINIMUM_GROWTH_CHARACTERS = 8
    }
}

internal object TerminalStreamTextSanitizer {
    private val promptPrefix = Regex("""^(?:\([^)]*\)\s*)?(?:[>$#]|[›❯➜])(?:\s|$)""")
    private val leadingDecoration = Regex("""^[\s*•⏺✦✧✻✽●○◆◇└├│]+""")
    private val whitespace = Regex("""\s+""")
    private val statusLine = Regex(
        """^(?:thinking|working|loading|generating|responding)(?:\s|[.·(]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val ignoredFragments = listOf(
        "esc to interrupt",
        "ctrl+c to",
        "? for shortcuts",
        "shift+tab",
        "tokens left",
        "context left",
        "bypass permissions",
        "auto-accept",
        "press enter to",
        "type your message"
    )

    fun sanitize(raw: String): String? {
        var text = raw
            .replace(CharUtils.DWC.toString(), "")
            .replace('\u0000', ' ')
            .replace('\t', ' ')
            .trim()
        if (text.isBlank() || promptPrefix.containsMatchIn(text)) return null

        val lowercase = text.lowercase()
        if (ignoredFragments.any(lowercase::contains)) return null

        text = leadingDecoration.replace(text, "")
        text = whitespace.replace(text, " ").trim()
        if (text.length < MINIMUM_TEXT_LENGTH || text.none(Char::isLetterOrDigit)) return null
        if (statusLine.containsMatchIn(text)) return null

        return if (text.length <= MAXIMUM_TEXT_LENGTH) {
            text
        } else {
            text.take(MAXIMUM_TEXT_LENGTH - ELLIPSIS.length).trimEnd() + ELLIPSIS
        }
    }

    private const val MINIMUM_TEXT_LENGTH = 3
    private const val MAXIMUM_TEXT_LENGTH = 96
    private const val ELLIPSIS = "..."
}
