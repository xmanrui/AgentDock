package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class TerminalActivityEvent {
    Started,
    Completed
}

internal object TerminalActivityEventParser {
    fun parse(providerId: String, line: String): TerminalActivityEvent? {
        val json = parseObject(line) ?: return null
        return when (providerId) {
            CLIProvider.CODEX_ID -> parseCodex(json)
            CLIProvider.CLAUDE_CODE_ID -> parseClaudeCode(json)
            else -> null
        }
    }

    private fun parseCodex(json: JsonObject): TerminalActivityEvent? {
        if (json.string("type") != "event_msg") return null
        return when (json.obj("payload")?.string("type")) {
            "task_started" -> TerminalActivityEvent.Started
            "task_complete", "turn_aborted" -> TerminalActivityEvent.Completed
            else -> null
        }
    }

    private fun parseClaudeCode(json: JsonObject): TerminalActivityEvent? {
        if (json.string("type") == "system" && json.string("subtype") == "turn_duration") {
            return TerminalActivityEvent.Completed
        }
        if (json.string("type") != "user" || json.boolean("isMeta") == true) return null
        val message = json.obj("message")
        if (message?.string("role") != null && message.string("role") != "user") return null
        val content = message?.get("content") ?: json.get("content")
        return if (hasPromptText(content)) TerminalActivityEvent.Started else null
    }

    private fun hasPromptText(content: JsonElement?): Boolean {
        if (content == null || content.isJsonNull) return false
        if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
            return content.asString.isNotBlank()
        }
        if (!content.isJsonArray) return false
        return content.asJsonArray.any { element ->
            if (!element.isJsonObject) return@any false
            val block = element.asJsonObject
            val type = block.string("type")
            type in TEXT_BLOCK_TYPES && block.string("text")?.isNotBlank() == true
        }
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

    private val TEXT_BLOCK_TYPES = setOf(null, "text", "input_text")
}
