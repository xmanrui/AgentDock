package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import com.agentdock.util.SessionTextSanitizer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

data class SessionContentPreview(
    val messages: List<SessionContentMessage>,
    val omittedMessageCount: Int = 0,
    val notice: String? = null
)

data class SessionContentMessage(
    val role: SessionContentRole,
    val text: String
)

enum class SessionContentRole {
    User,
    Assistant
}

class LocalSessionContentService(
    private val userHome: File = File(System.getProperty("user.home"))
) {
    private val sourceFiles = ConcurrentHashMap<String, File>()
    private val codexSourceFiles = ConcurrentHashMap<String, File>()
    private val previews = ConcurrentHashMap<String, CachedPreview>()
    private val codexSourceIndexLock = Any()
    @Volatile
    private var lastCodexSourceScanAt = 0L

    fun warmSourceIndex() {
        runCatching { refreshCodexSourceIndexIfNeeded() }
    }

    fun load(session: AgentSession): SessionContentPreview {
        val source = runCatching { findSourceFile(session) }.getOrNull() ?: return fallbackPreview(
            session,
            "Full local conversation is not available yet."
        )
        val modifiedAt = source.lastModified()
        val length = source.length()
        previews[source.absolutePath]?.let { cached ->
            if (cached.modifiedAt == modifiedAt && cached.length == length) {
                return cached.preview
            }
        }

        val preview = runCatching {
            parseSource(source, session.providerId)
        }.getOrElse {
            fallbackPreview(session, "The local conversation could not be read.")
        }
        previews[source.absolutePath] = CachedPreview(modifiedAt, length, preview)
        return preview
    }

    private fun findSourceFile(session: AgentSession): File? {
        val providerSessionId = session.providerSessionId?.takeIf { it.isSafeFileName() } ?: return null
        val key = "${session.providerId}:${session.projectPath}:$providerSessionId"
        sourceFiles[key]?.takeIf { it.isFile }?.let { return it }

        val source = when (session.providerId) {
            CLIProvider.CODEX_ID -> findCodexSource(providerSessionId)
            CLIProvider.CLAUDE_CODE_ID -> findClaudeCodeSource(session, providerSessionId)
            else -> null
        }
        if (source != null) {
            sourceFiles[key] = source
        }
        return source
    }

    private fun findCodexSource(providerSessionId: String): File? {
        codexSourceFiles[providerSessionId]?.takeIf { it.isFile }?.let { return it }
        refreshCodexSourceIndexIfNeeded()
        return codexSourceFiles[providerSessionId]?.takeIf { it.isFile }
    }

    private fun refreshCodexSourceIndexIfNeeded() {
        val sessionsDir = File(userHome, ".codex/sessions")
        if (!sessionsDir.isDirectory) return

        val now = System.currentTimeMillis()
        if (lastCodexSourceScanAt == 0L || now - lastCodexSourceScanAt >= CODEX_INDEX_REFRESH_MS) {
            synchronized(codexSourceIndexLock) {
                if (lastCodexSourceScanAt == 0L || now - lastCodexSourceScanAt >= CODEX_INDEX_REFRESH_MS) {
                    sessionsDir.walkTopDown()
                        .maxDepth(CODEX_SESSION_DIRECTORY_DEPTH)
                        .filter { file ->
                            file.isFile && file.extension == "jsonl" && file.name.startsWith("rollout-")
                        }
                        .forEach { file ->
                            val sessionId = CODEX_SESSION_ID_PATTERN.find(file.nameWithoutExtension)?.value
                                ?: return@forEach
                            codexSourceFiles.compute(sessionId) { _, current ->
                                if (current == null || file.lastModified() > current.lastModified()) file else current
                            }
                        }
                    lastCodexSourceScanAt = now
                }
            }
        }
    }

    private fun findClaudeCodeSource(session: AgentSession, providerSessionId: String): File? {
        val projectPath = session.projectPath.takeIf { it.isNotBlank() } ?: return null
        val projectDirectoryName = File(projectPath).absolutePath.replace(File.separatorChar, '-')
        val directSource = File(File(File(userHome, ".claude/projects"), projectDirectoryName), "$providerSessionId.jsonl")
        if (directSource.isFile) return directSource

        val projectsDir = File(userHome, ".claude/projects")
        if (!projectsDir.isDirectory) return null
        return projectsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .map { directory -> File(directory, "$providerSessionId.jsonl") }
            .firstOrNull { it.isFile }
    }

    private fun parseSource(source: File, providerId: String): SessionContentPreview {
        val retained = RetainedMessages()
        source.useLines { lines ->
            lines.forEach { line ->
                val json = parseObject(line) ?: return@forEach
                val message = when (providerId) {
                    CLIProvider.CODEX_ID -> parseCodexMessage(json)
                    CLIProvider.CLAUDE_CODE_ID -> parseClaudeCodeMessage(json)
                    else -> null
                }
                if (message != null) {
                    retained.add(message)
                }
            }
        }
        return retained.toPreview()
    }

    private fun parseCodexMessage(json: JsonObject): SessionContentMessage? {
        if (json.string("type") != "response_item") return null
        val payload = json.obj("payload") ?: return null
        if (payload.string("type") != "message") return null
        val role = payload.string("role").toContentRole() ?: return null
        return createMessage(role, textFromContent(payload.get("content")))
    }

    private fun parseClaudeCodeMessage(json: JsonObject): SessionContentMessage? {
        if (json.boolean("isMeta") == true) return null
        val outerRole = json.string("type").toContentRole() ?: return null
        val message = json.obj("message")
        val role = message?.string("role").toContentRole() ?: outerRole
        val content = message?.get("content") ?: json.get("content")
        return createMessage(role, textFromContent(content))
    }

    private fun createMessage(role: SessionContentRole, rawText: String?): SessionContentMessage? {
        val text = sanitizeMessage(rawText)
        if (text.isBlank()) return null
        return SessionContentMessage(role, text.take(MAX_MESSAGE_CHARS).trimEnd().appendTruncation(text))
    }

    private fun sanitizeMessage(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""
        var text = rawText.replace("\r\n", "\n").replace('\r', '\n')
        HIDDEN_CONTEXT_BLOCKS.forEach { pattern ->
            text = text.replace(pattern, "\n")
        }
        return text.lineSequence()
            .map { line -> line.trimEnd() }
            .dropWhile { it.isBlank() }
            .toList()
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun textFromContent(content: JsonElement?): String? {
        if (content == null || content.isJsonNull) return null
        if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
            return content.asString
        }
        if (content.isJsonArray) {
            return content.asJsonArray
                .mapNotNull { item -> textFromContentBlock(item) }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        }
        if (content.isJsonObject) {
            return textFromContentBlock(content)
        }
        return null
    }

    private fun textFromContentBlock(element: JsonElement): String? {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            return element.asString
        }
        if (!element.isJsonObject) return null
        val block = element.asJsonObject
        val type = block.string("type")
        if (type != null && type !in TEXT_CONTENT_TYPES) return null
        return block.string("text")
            ?: block.get("content")?.let { textFromContent(it) }
    }

    private fun fallbackPreview(session: AgentSession, notice: String): SessionContentPreview {
        val summary = SessionTextSanitizer.summary(session.summary)
            .ifBlank { SessionTextSanitizer.summary(session.name) }
        val messages = summary.takeIf { it.isNotBlank() }
            ?.let { listOf(SessionContentMessage(SessionContentRole.User, it)) }
            .orEmpty()
        return SessionContentPreview(messages = messages, notice = notice)
    }

    private fun parseObject(line: String): JsonObject? {
        return try {
            JsonParser.parseString(line).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) value.asBoolean else null
    }

    private fun String?.toContentRole(): SessionContentRole? {
        return when (this) {
            "user" -> SessionContentRole.User
            "assistant" -> SessionContentRole.Assistant
            else -> null
        }
    }

    private fun String.isSafeFileName(): Boolean {
        return isNotBlank() && this != "." && this != ".." &&
            !contains('/') && !contains('\\') && !contains(File.separatorChar)
    }

    private fun String.appendTruncation(original: String): String {
        return if (length < original.trimEnd().length) "$this..." else this
    }

    private class RetainedMessages {
        private var first: SessionContentMessage? = null
        private val recent = ArrayDeque<SessionContentMessage>()
        private var acceptedCount = 0
        private var previous: SessionContentMessage? = null

        fun add(message: SessionContentMessage) {
            if (message == previous) return
            previous = message
            acceptedCount += 1
            if (first == null) {
                first = message
                return
            }
            if (recent.size == RECENT_MESSAGE_LIMIT) {
                recent.removeFirst()
            }
            recent.addLast(message)
        }

        fun toPreview(): SessionContentPreview {
            val messages = buildList {
                first?.let { add(it) }
                addAll(recent)
            }
            if (messages.isEmpty()) {
                return SessionContentPreview(
                    messages = emptyList(),
                    notice = "No readable conversation messages were found."
                )
            }
            return SessionContentPreview(
                messages = messages,
                omittedMessageCount = (acceptedCount - messages.size).coerceAtLeast(0)
            )
        }
    }

    private data class CachedPreview(
        val modifiedAt: Long,
        val length: Long,
        val preview: SessionContentPreview
    )

    companion object {
        private const val CODEX_SESSION_DIRECTORY_DEPTH = 8
        private const val CODEX_INDEX_REFRESH_MS = 30_000L
        private const val MAX_MESSAGE_CHARS = 2_200
        private const val RECENT_MESSAGE_LIMIT = 9
        private val CODEX_SESSION_ID_PATTERN =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val TEXT_CONTENT_TYPES = setOf("text", "input_text", "output_text")
        private val HIDDEN_CONTEXT_BLOCKS = listOf(
            Regex("<environment_context[\\s\\S]*?</environment_context>", RegexOption.IGNORE_CASE),
            Regex("<codex_internal_context[\\s\\S]*?</codex_internal_context>", RegexOption.IGNORE_CASE)
        )
    }
}
