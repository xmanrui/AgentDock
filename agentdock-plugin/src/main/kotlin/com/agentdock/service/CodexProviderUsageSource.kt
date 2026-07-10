package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult
import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.model.ProviderUsageWindow
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal class CodexProviderUsageSource(
    private val detectionService: ProviderDetectionService = ProviderDetectionService(),
    private val client: CodexRateLimitClient = CodexAppServerRateLimitClient()
) : ProviderUsageSource {
    override fun load(provider: CLIProvider): ProviderUsageSnapshot {
        val executablePath = when (val detection = detectionService.detect(provider)) {
            is ProviderDetectionResult.Available -> detection.executablePath
            is ProviderDetectionResult.Disabled -> {
                return ProviderUsageSnapshot.unavailable(provider, "Codex is disabled in provider settings.")
            }
            is ProviderDetectionResult.Missing -> {
                return ProviderUsageSnapshot.unavailable(provider, "Codex CLI is not available.")
            }
        }
        return CodexUsageResponseParser.parse(provider, client.read(executablePath))
    }
}

internal fun interface CodexRateLimitClient {
    fun read(executablePath: String): JsonObject
}

internal class CodexAppServerRateLimitClient(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : CodexRateLimitClient {
    override fun read(executablePath: String): JsonObject {
        val processBuilder = ProcessBuilder(executablePath, "app-server", "--stdio")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        val executableDirectory = runCatching { Path.of(executablePath).toAbsolutePath().parent }.getOrNull()
        if (executableDirectory != null) {
            val existingPath = processBuilder.environment()["PATH"].orEmpty()
            processBuilder.environment()["PATH"] = executableDirectory.toString() +
                if (existingPath.isBlank()) "" else File.pathSeparator + existingPath
        }
        val process = processBuilder.start()
        val timedOut = AtomicBoolean(false)
        val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "AgentDock Codex usage timeout").apply { isDaemon = true }
        }
        val timeoutTask = timeoutExecutor.schedule({
            timedOut.set(true)
            process.destroyForcibly()
        }, timeoutMillis, TimeUnit.MILLISECONDS)

        try {
            process.outputStream.bufferedWriter().use { writer ->
                process.inputStream.bufferedReader().use { reader ->
                    writer.write(INITIALIZE_REQUEST)
                    writer.newLine()
                    writer.flush()

                    var rateLimitRequested = false
                    while (true) {
                        val line = reader.readLine() ?: break
                        val message = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: continue
                        when (message.requestId()) {
                            INITIALIZE_REQUEST_ID -> if (!rateLimitRequested) {
                                writer.write(INITIALIZED_NOTIFICATION)
                                writer.newLine()
                                writer.write(RATE_LIMIT_REQUEST)
                                writer.newLine()
                                writer.flush()
                                rateLimitRequested = true
                            }
                            RATE_LIMIT_REQUEST_ID -> {
                                val result = message.get("result")
                                if (result != null && result.isJsonObject) {
                                    return result.asJsonObject
                                }
                                throw IOException("Codex app-server rejected the usage request")
                            }
                        }
                    }
                }
            }
            if (timedOut.get()) {
                throw TimeoutException("Codex usage request timed out")
            }
            throw IOException("Codex app-server closed before returning usage limits")
        } finally {
            timeoutTask.cancel(true)
            timeoutExecutor.shutdownNow()
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun JsonObject.requestId(): Int? {
        val id = get("id") ?: return null
        return if (id.isJsonPrimitive && id.asJsonPrimitive.isNumber) id.asInt else null
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 8_000L
        private const val INITIALIZE_REQUEST_ID = 1
        private const val RATE_LIMIT_REQUEST_ID = 2
        private const val INITIALIZE_REQUEST =
            "{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"agentdock\",\"version\":\"1\"},\"capabilities\":{\"experimentalApi\":true}}}"
        private const val INITIALIZED_NOTIFICATION = "{\"method\":\"initialized\"}"
        private const val RATE_LIMIT_REQUEST =
            "{\"id\":2,\"method\":\"account/rateLimits/read\",\"params\":null}"
    }
}

internal object CodexUsageResponseParser {
    private data class ParsedWindow(
        val usage: ProviderUsageWindow,
        val durationMinutes: Long?
    )

    fun parse(provider: CLIProvider, response: JsonObject): ProviderUsageSnapshot {
        val rateLimits = response.objectValue("rateLimitsByLimitId")
            ?.objectValue(CLIProvider.CODEX_ID)
            ?: response.objectValue("rateLimits")
            ?: return ProviderUsageSnapshot.unavailable(provider, "Codex did not report plan usage limits.")
        val primary = rateLimits.window("primary")
        val secondary = rateLimits.window("secondary")
        val windows = listOfNotNull(primary, secondary)
        val fiveHour = windows.firstOrNull { it.durationMinutes == FIVE_HOUR_MINUTES }
            ?: primary?.takeIf { it.durationMinutes == null || it.durationMinutes < ONE_DAY_MINUTES }
        val weekly = windows.firstOrNull { it.durationMinutes == WEEK_MINUTES }
            ?: secondary?.takeIf { it !== fiveHour }
            ?: primary?.takeIf { it.durationMinutes != null && it.durationMinutes >= ONE_DAY_MINUTES }

        if (fiveHour == null && weekly == null) {
            return ProviderUsageSnapshot.unavailable(provider, "Codex did not report plan usage limits.")
        }
        val resetCount = response.objectValue("rateLimitResetCredits")?.longValue("availableCount")
        return ProviderUsageSnapshot(
            providerId = provider.id,
            providerName = provider.displayName,
            status = ProviderUsageSnapshot.STATUS_AVAILABLE,
            fiveHour = fiveHour?.usage,
            weekly = weekly?.usage,
            resetCount = resetCount
        )
    }

    private fun JsonObject.window(name: String): ParsedWindow? {
        val value = objectValue(name) ?: return null
        val usedPercent = value.doubleValue("usedPercent")?.roundToInt()?.coerceIn(0, 100) ?: return null
        return ParsedWindow(
            usage = ProviderUsageWindow(
                usedPercent = usedPercent,
                resetsAtEpochSeconds = value.longValue("resetsAt")
            ),
            durationMinutes = value.longValue("windowDurationMins")
        )
    }

    private fun JsonObject.objectValue(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun JsonObject.longValue(name: String): Long? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asLong else null
    }

    private fun JsonObject.doubleValue(name: String): Double? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asDouble else null
    }

    private const val FIVE_HOUR_MINUTES = 300L
    private const val ONE_DAY_MINUTES = 1_440L
    private const val WEEK_MINUTES = 10_080L
}
