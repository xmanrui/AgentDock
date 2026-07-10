package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.model.ProviderUsageWindow
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

internal class ClaudeProviderUsageSource(
    private val configurationResolver: ClaudeUsageConfigurationResolver = ClaudeUsageConfigurationResolver(),
    private val client: ClaudeUsageClient = ClaudeUsageHttpClient()
) : ProviderUsageSource {
    override fun load(provider: CLIProvider): ProviderUsageSnapshot {
        if (!provider.enabled) {
            return ProviderUsageSnapshot.unavailable(provider, "Claude Code is disabled in provider settings.")
        }
        val configuration = configurationResolver.resolve()
            ?: return ProviderUsageSnapshot.unauthenticated(
                provider,
                "Claude Code usage credentials are not available to AgentDock."
            )
        val response = client.read(configuration)
        return when (response.statusCode) {
            in 200..299 -> ClaudeUsageResponseParser.parse(provider, response.body)
            401, 403 -> ProviderUsageSnapshot.unauthenticated(
                provider,
                "Sign in to Claude Code with a subscription account to view usage."
            )
            404 -> ProviderUsageSnapshot.unavailable(
                provider,
                "The current Claude endpoint does not expose plan usage."
            )
            else -> ProviderUsageSnapshot.unavailable(provider, "Could not load Claude usage limits right now.")
        }
    }
}

internal data class ClaudeUsageConfiguration(
    val usageUri: URI,
    val bearerToken: String
)

internal class ClaudeUsageConfigurationResolver(
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: Path = Path.of(System.getProperty("user.home")),
    private val secureCredentialsReader: ClaudeSecureCredentialsReader = MacClaudeKeychainCredentialsReader()
) {
    fun resolve(): ClaudeUsageConfiguration? {
        val configuredDirectory = environment[CLAUDE_CONFIG_DIR]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val configDirectory = configuredDirectory?.let(Path::of) ?: userHome.resolve(".claude")
        val settingsEnvironment = readSettingsEnvironment(configDirectory.resolve("settings.json"))
        val baseUrl = firstValue(ANTHROPIC_BASE_URL, settingsEnvironment)
            ?: DEFAULT_ANTHROPIC_BASE_URL
        val explicitlyConfiguredToken = firstValue(CLAUDE_CODE_OAUTH_TOKEN, settingsEnvironment)
            ?: firstValue(ANTHROPIC_AUTH_TOKEN, settingsEnvironment)
        val storedCredentialsToken = if (isOfficialAnthropicBaseUrl(baseUrl)) {
            storedCredentialsAccessToken(configDirectory, configuredDirectory != null)
        } else {
            null
        }
        val bearerToken = explicitlyConfiguredToken
            ?: storedCredentialsToken
            ?: return null
        val usageUri = usageUri(baseUrl) ?: return null
        return ClaudeUsageConfiguration(usageUri, bearerToken)
    }

    private fun firstValue(name: String, settingsEnvironment: Map<String, String>): String? {
        return environment[name]?.trim()?.takeIf { it.isNotEmpty() }
            ?: settingsEnvironment[name]?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun readSettingsEnvironment(path: Path): Map<String, String> {
        val root = readJsonObject(path) ?: return emptyMap()
        val values = root.get("env")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        return values.entrySet().mapNotNull { (name, value) ->
            value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.let { name to it }
        }.toMap()
    }

    private fun readCredentialsAccessToken(path: Path): String? {
        val root = readJsonObject(path) ?: return null
        return credentialsAccessToken(root)
    }

    private fun storedCredentialsAccessToken(configDirectory: Path, customConfigDirectory: Boolean): String? {
        return readCredentialsAccessToken(configDirectory.resolve(".credentials.json"))
            ?: secureCredentialsReader.read(configDirectory, customConfigDirectory)
                ?.let { payload ->
                    runCatching { JsonParser.parseString(payload).asJsonObject }
                        .getOrNull()
                        ?.let(::credentialsAccessToken)
                }
    }

    private fun credentialsAccessToken(root: JsonObject): String? {
        val oauth = root.get("claudeAiOauth")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        return listOf("accessToken", "access_token")
            .asSequence()
            .mapNotNull { name ->
                oauth.get(name)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .firstOrNull()
    }

    private fun readJsonObject(path: Path): JsonObject? {
        if (!Files.isRegularFile(path)) return null
        return runCatching {
            Files.newBufferedReader(path).use { reader -> JsonParser.parseReader(reader).asJsonObject }
        }.getOrNull()
    }

    private fun usageUri(baseUrl: String): URI? {
        val base = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
        val scheme = base.scheme?.lowercase() ?: return null
        if (scheme != "https" && !(scheme == "http" && base.host.isLoopbackHost())) {
            return null
        }
        return runCatching { base.resolve(USAGE_PATH) }.getOrNull()
    }

    private fun isOfficialAnthropicBaseUrl(baseUrl: String): Boolean {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("api.anthropic.com", ignoreCase = true)
    }

    private fun String?.isLoopbackHost(): Boolean {
        val value = this?.lowercase() ?: return false
        return value == "localhost" || value == "127.0.0.1" || value == "::1" || value == "[::1]"
    }

    companion object {
        private const val CLAUDE_CONFIG_DIR = "CLAUDE_CONFIG_DIR"
        private const val CLAUDE_CODE_OAUTH_TOKEN = "CLAUDE_CODE_OAUTH_TOKEN"
        private const val ANTHROPIC_AUTH_TOKEN = "ANTHROPIC_AUTH_TOKEN"
        private const val ANTHROPIC_BASE_URL = "ANTHROPIC_BASE_URL"
        private const val DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com"
        private const val USAGE_PATH = "/api/oauth/usage"
    }
}

internal fun interface ClaudeSecureCredentialsReader {
    fun read(configDirectory: Path, customConfigDirectory: Boolean): String?
}

internal class MacClaudeKeychainCredentialsReader(
    private val accountName: String = System.getenv("USER")
        ?.takeIf { it.matches(Regex("[a-zA-Z0-9._-]+")) }
        ?: System.getProperty("user.name")?.takeIf { it.matches(Regex("[a-zA-Z0-9._-]+")) }
        ?: "claude-code-user"
) : ClaudeSecureCredentialsReader {
    override fun read(configDirectory: Path, customConfigDirectory: Boolean): String? {
        val security = Path.of(SECURITY_COMMAND)
        if (!Files.isExecutable(security)) return null
        val suffix = if (customConfigDirectory) "-${configDirectory.storageHash()}" else ""
        val serviceName = "Claude Code-credentials$suffix"
        return runCatching {
            val process = ProcessBuilder(
                security.toString(),
                "find-generic-password",
                "-a",
                accountName,
                "-w",
                "-s",
                serviceName
            )
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val completed = process.waitFor(KEYCHAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@runCatching null
            }
            if (process.exitValue() != 0) return@runCatching null
            process.inputStream.bufferedReader().use { it.readText() }
                .trim()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun Path.storageHash(): String {
        val normalized = Normalizer.normalize(toAbsolutePath().normalize().toString(), Normalizer.Form.NFC)
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(8)
    }

    companion object {
        private const val SECURITY_COMMAND = "/usr/bin/security"
        private const val KEYCHAIN_TIMEOUT_MILLIS = 2_000L
    }
}

internal data class ClaudeUsageHttpResponse(
    val statusCode: Int,
    val body: String
)

internal fun interface ClaudeUsageClient {
    fun read(configuration: ClaudeUsageConfiguration): ClaudeUsageHttpResponse
}

internal class ClaudeUsageHttpClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
) : ClaudeUsageClient {
    override fun read(configuration: ClaudeUsageConfiguration): ClaudeUsageHttpResponse {
        val request = HttpRequest.newBuilder(configuration.usageUri)
            .timeout(Duration.ofSeconds(6))
            .header("Authorization", "Bearer ${configuration.bearerToken}")
            .header("Content-Type", "application/json")
            .header("anthropic-beta", "oauth-2025-04-20")
            .header("User-Agent", "AgentDock")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return ClaudeUsageHttpResponse(response.statusCode(), response.body())
    }
}

internal object ClaudeUsageResponseParser {
    fun parse(provider: CLIProvider, body: String): ProviderUsageSnapshot {
        val response = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: return ProviderUsageSnapshot.unavailable(provider, "Claude returned an unreadable usage response.")
        val fiveHour = response.window("five_hour")
        val weekly = response.window("seven_day")
        if (fiveHour == null && weekly == null) {
            return ProviderUsageSnapshot.unavailable(provider, "Claude did not report plan usage limits.")
        }
        return ProviderUsageSnapshot(
            providerId = provider.id,
            providerName = provider.displayName,
            status = ProviderUsageSnapshot.STATUS_AVAILABLE,
            fiveHour = fiveHour,
            weekly = weekly
        )
    }

    private fun JsonObject.window(name: String): ProviderUsageWindow? {
        val value = get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val usedPercent = value.number("utilization")
            ?: value.number("used_percentage")
            ?: return null
        return ProviderUsageWindow(
            usedPercent = usedPercent.roundToInt().coerceIn(0, 100),
            resetsAtEpochSeconds = value.epochSeconds("resets_at")
        )
    }

    private fun JsonObject.number(name: String): Double? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asDouble else null
    }

    private fun JsonObject.epochSeconds(name: String): Long? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        if (primitive.isNumber) {
            val timestamp = primitive.asLong
            return if (timestamp > MILLISECOND_EPOCH_THRESHOLD) timestamp / 1_000 else timestamp
        }
        if (!primitive.isString) return null
        val text = primitive.asString.trim()
        text.toLongOrNull()?.let { timestamp ->
            return if (timestamp > MILLISECOND_EPOCH_THRESHOLD) timestamp / 1_000 else timestamp
        }
        return runCatching { Instant.parse(text).epochSecond }.getOrNull()
    }

    private const val MILLISECOND_EPOCH_THRESHOLD = 10_000_000_000L
}
