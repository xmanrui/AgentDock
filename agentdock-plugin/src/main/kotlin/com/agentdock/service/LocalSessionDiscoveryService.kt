package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider
import com.agentdock.util.ProjectIdentity
import com.agentdock.util.SessionTextSanitizer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.time.Instant

class LocalSessionDiscoveryService(
    private val userHome: File = File(System.getProperty("user.home"))
) {
    fun discover(projectPath: String): List<AgentSession> {
        val projectDir = File(projectPath).normalizeFile()
        if (!projectDir.isDirectory) return emptyList()

        return buildList {
            addAll(discoverCodex(projectDir))
            addAll(discoverClaudeCode(projectDir))
            addAll(discoverGemini(projectDir))
        }.sortedByDescending { it.updatedAt }
    }

    private fun discoverCodex(projectDir: File): List<AgentSession> {
        val codexHome = File(userHome, ".codex")
        val sessionsDir = File(codexHome, "sessions")
        if (!sessionsDir.isDirectory) return emptyList()

        val index = readCodexIndex(File(codexHome, "session_index.jsonl"))
        return sessionsDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "jsonl" && it.name.startsWith("rollout-") }
            .mapNotNull { file -> parseCodexSession(file, projectDir, index[file.codexSessionIdFromName()]) }
            .toList()
    }

    private fun parseCodexSession(file: File, projectDir: File, indexEntry: CodexIndexEntry?): AgentSession? {
        var sessionId: String? = file.codexSessionIdFromName()
        var cwd: String? = null
        var firstUserMessage: String? = null
        var firstTimestamp = 0L
        var lastTimestamp = file.lastModified()
        var foundSessionMetadata = false

        file.useLines { lines ->
            lines.forEach { line ->
                val json = parseObject(line) ?: return@forEach
                val timestamp = json.string("timestamp")?.toMillis() ?: 0L
                if (timestamp > 0L) {
                    if (firstTimestamp == 0L) firstTimestamp = timestamp
                    lastTimestamp = timestamp
                }

                if (json.string("type") == "session_meta" && !foundSessionMetadata) {
                    val payload = json.obj("payload")
                    foundSessionMetadata = true
                    if (payload?.isCodexSubagentSource() == true) return null
                    sessionId = payload?.string("id") ?: sessionId
                    cwd = payload?.string("cwd") ?: cwd
                }

                if (firstUserMessage == null && json.string("type") == "response_item") {
                    val payload = json.obj("payload")
                    val role = payload?.string("role")
                    val itemType = payload?.string("type")
                    if (role == "user" && itemType == "message") {
                        val candidate = payload.textFromContent()
                        if (SessionTextSanitizer.summary(candidate).isNotBlank()) {
                            firstUserMessage = candidate
                        }
                    }
                }
            }
        }

        val sessionCwd = cwd?.takeIf { it.isNotBlank() }?.let { File(it).normalizeFile() } ?: return null
        if (!sessionCwd.belongsToProject(projectDir)) return null
        val providerSessionId = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val title = indexEntry?.threadName
            ?: firstUserMessage?.toTitle(providerSessionId)
            ?: "Codex session ${providerSessionId.take(8)}"

        return AgentSession(
            id = "${CLIProvider.CODEX_ID}:$providerSessionId",
            projectId = ProjectIdentity.idFor(projectDir.absolutePath),
            projectPath = projectDir.absolutePath,
            name = title,
            providerId = CLIProvider.CODEX_ID,
            status = AgentSessionStatus.Restorable,
            cwd = sessionCwd.absolutePath,
            providerSessionId = providerSessionId,
            historyFilePath = file.absolutePath,
            summary = SessionTextSanitizer.summary(firstUserMessage),
            linkedFiles = linkedProjectPath(projectDir, sessionCwd),
            createdAt = firstTimestamp.takeIf { it > 0L } ?: lastTimestamp,
            updatedAt = indexEntry?.updatedAt?.takeIf { it > 0L } ?: lastTimestamp
        )
    }

    private fun readCodexIndex(indexFile: File): Map<String, CodexIndexEntry> {
        if (!indexFile.isFile) return emptyMap()
        return buildMap {
            indexFile.useLines { lines ->
                lines.forEach { line ->
                    val json = parseObject(line) ?: return@forEach
                    val id = json.string("id")?.takeIf { it.isNotBlank() } ?: return@forEach
                    put(
                        id,
                        CodexIndexEntry(
                            threadName = json.string("thread_name")?.let { SessionTextSanitizer.title(it) },
                            updatedAt = json.string("updated_at")?.toMillis() ?: 0L
                        )
                    )
                }
            }
        }
    }

    private fun discoverClaudeCode(projectDir: File): List<AgentSession> {
        val projectHistoryDir = File(File(userHome, ".claude/projects"), projectDir.claudeProjectDirectoryName())
        if (!projectHistoryDir.isDirectory) return emptyList()

        return projectHistoryDir
            .listFiles { file -> file.isFile && file.extension == "jsonl" }
            .orEmpty()
            .mapNotNull { parseClaudeCodeSession(it, projectDir) }
    }

    private fun parseClaudeCodeSession(file: File, projectDir: File): AgentSession? {
        var sessionId: String? = file.nameWithoutExtension
        var cwd: String? = null
        var firstUserMessage: String? = null
        var firstTimestamp = 0L
        var lastTimestamp = file.lastModified()

        file.useLines { lines ->
            lines.forEach { line ->
                val json = parseObject(line) ?: return@forEach
                sessionId = json.string("sessionId") ?: sessionId
                cwd = json.string("cwd") ?: cwd
                val timestamp = json.string("timestamp")?.toMillis() ?: 0L
                if (timestamp > 0L) {
                    if (firstTimestamp == 0L) firstTimestamp = timestamp
                    lastTimestamp = timestamp
                }

                if (firstUserMessage == null && json.string("type") == "user") {
                    val candidate = json.textFromContent()
                        ?: json.obj("message").textFromContent()
                    if (SessionTextSanitizer.summary(candidate).isNotBlank()) {
                        firstUserMessage = candidate
                    }
                }
            }
        }

        val sessionCwd = cwd?.takeIf { it.isNotBlank() }?.let { File(it).normalizeFile() } ?: projectDir
        if (!sessionCwd.belongsToProject(projectDir)) return null
        val providerSessionId = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val title = firstUserMessage?.toTitle(providerSessionId) ?: "Claude Code session ${providerSessionId.take(8)}"

        return AgentSession(
            id = "${CLIProvider.CLAUDE_CODE_ID}:$providerSessionId",
            projectId = ProjectIdentity.idFor(projectDir.absolutePath),
            projectPath = projectDir.absolutePath,
            name = title,
            providerId = CLIProvider.CLAUDE_CODE_ID,
            status = AgentSessionStatus.Restorable,
            cwd = sessionCwd.absolutePath,
            providerSessionId = providerSessionId,
            historyFilePath = file.absolutePath,
            summary = SessionTextSanitizer.summary(firstUserMessage),
            linkedFiles = linkedProjectPath(projectDir, sessionCwd),
            createdAt = firstTimestamp.takeIf { it > 0L } ?: lastTimestamp,
            updatedAt = lastTimestamp
        )
    }

    private fun discoverGemini(projectDir: File): List<AgentSession> {
        val projectHash = sha256(projectDir.absolutePath)
        val chatsDir = File(userHome, ".gemini/tmp/$projectHash/chats")
        if (!chatsDir.isDirectory) return emptyList()

        return chatsDir.listFiles { file ->
            file.isFile && file.extension == "json" && file.name.startsWith("session-")
        }.orEmpty().mapNotNull { file -> parseGeminiSession(file, projectDir, projectHash) }
    }

    private fun parseGeminiSession(file: File, projectDir: File, projectHash: String): AgentSession? {
        val json = parseFileObject(file) ?: return null
        if (json.string("projectHash")?.takeIf { it.isNotBlank() } != projectHash) return null
        val providerSessionId = json.string("sessionId")?.takeIf { it.isNotBlank() } ?: return null
        val messages = json.array("messages")
        val firstUserMessage = messages?.asSequence()
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            ?.firstOrNull { it.string("type") == "user" }
            ?.string("content")
            ?.takeIf { SessionTextSanitizer.summary(it).isNotBlank() }
        val storedSummary = json.string("summary")?.takeIf { SessionTextSanitizer.summary(it).isNotBlank() }
        val createdAt = json.string("startTime")?.toMillis() ?: file.lastModified()
        val updatedAt = json.string("lastUpdated")?.toMillis() ?: file.lastModified()
        val titleSource = firstUserMessage ?: storedSummary
        val title = titleSource?.toTitle(providerSessionId) ?: "Gemini CLI session ${providerSessionId.take(8)}"

        return AgentSession(
            id = "${CLIProvider.GEMINI_ID}:$providerSessionId",
            projectId = ProjectIdentity.idFor(projectDir.absolutePath),
            projectPath = projectDir.absolutePath,
            name = title,
            providerId = CLIProvider.GEMINI_ID,
            status = AgentSessionStatus.Restorable,
            cwd = projectDir.absolutePath,
            providerSessionId = providerSessionId,
            historyFilePath = file.absolutePath,
            summary = SessionTextSanitizer.summary(firstUserMessage ?: storedSummary),
            linkedFiles = mutableListOf("."),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseObject(line: String): JsonObject? {
        return try {
            JsonParser.parseString(line).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFileObject(file: File): JsonObject? {
        return try {
            file.reader().use { reader ->
                JsonParser.parseReader(reader).takeIf { it.isJsonObject }?.asJsonObject
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.isCodexSubagentSource(): Boolean {
        val source = get("source") ?: return false
        return source.isJsonObject && source.asJsonObject.has("subagent")
    }

    private fun JsonObject?.textFromContent(): String? {
        if (this == null) return null
        return textFromContent(get("content"))
    }

    private fun textFromContent(content: JsonElement?): String? {
        if (content == null || content.isJsonNull) return null
        if (content.isJsonPrimitive && content.asJsonPrimitive.isString) return content.asString
        if (content.isJsonArray) {
            return content.asJsonArray
                .mapNotNull { item ->
                    val obj = item.takeIf { it.isJsonObject }?.asJsonObject
                    obj?.string("text")
                }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun String.toMillis(): Long {
        return try {
            Instant.parse(this).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun String.toTitle(providerSessionId: String): String {
        return SessionTextSanitizer.title(this, "Agent session ${providerSessionId.take(8)}")
    }

    private fun File.codexSessionIdFromName(): String? {
        return uuidPattern.find(nameWithoutExtension)?.value
    }

    private fun File.claudeProjectDirectoryName(): String = absolutePath.replace(File.separatorChar, '-')

    private fun File.normalizeFile(): File = toPath().normalize().toFile()

    private fun File.belongsToProject(projectDir: File): Boolean {
        val path = absolutePath
        val projectPath = projectDir.absolutePath
        return path == projectPath || path.startsWith("$projectPath${File.separator}")
    }

    private fun linkedProjectPath(projectDir: File, cwd: File): MutableList<String> {
        val relative = projectDir.toPath().relativize(cwd.toPath()).toString()
        return mutableListOf(if (relative.isBlank()) "." else relative)
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class CodexIndexEntry(
        val threadName: String?,
        val updatedAt: Long
    )

    companion object {
        private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
