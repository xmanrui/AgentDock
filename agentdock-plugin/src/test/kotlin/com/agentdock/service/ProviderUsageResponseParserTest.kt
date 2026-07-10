package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.util.OperatingSystem
import com.google.gson.JsonParser
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProviderUsageResponseParserTest {
    private val codex = CLIProvider.defaultProviders().first { it.id == CLIProvider.CODEX_ID }
    private val claude = CLIProvider.defaultProviders().first { it.id == CLIProvider.CLAUDE_CODE_ID }

    @Test
    fun `Codex app server client completes the initialize and rate limit exchange`() {
        if (OperatingSystem.current() == OperatingSystem.Windows) return
        val executable = Files.createTempFile("agentdock-codex-usage", ".sh")
        try {
            Files.writeString(
                executable,
                """
                    #!/bin/sh
                    while IFS= read -r line
                    do
                      case "${'$'}line" in
                        *'"id":1'*) printf '%s\n' '{"id":1,"result":{"userAgent":"test"}}' ;;
                        *'account/rateLimits/read'*) printf '%s\n' '{"id":2,"result":{"rateLimits":{"primary":{"usedPercent":12,"windowDurationMins":300}}}}' ;;
                      esac
                    done
                """.trimIndent()
            )
            executable.toFile().setExecutable(true)

            val response = CodexAppServerRateLimitClient(timeoutMillis = 2_000).read(executable.toString())

            assertEquals(12, response["rateLimits"].asJsonObject["primary"].asJsonObject["usedPercent"].asInt)
        } finally {
            Files.deleteIfExists(executable)
        }
    }

    @Test
    fun `parses Codex five hour weekly and reset credit limits`() {
        val response = JsonParser.parseString(
            """
                {
                  "rateLimits": {
                    "primary": {"usedPercent": 28, "windowDurationMins": 300, "resetsAt": 1783670295},
                    "secondary": {"usedPercent": 16, "windowDurationMins": 10080, "resetsAt": 1784239093}
                  },
                  "rateLimitResetCredits": {"availableCount": 2, "credits": null}
                }
            """.trimIndent()
        ).asJsonObject

        val usage = CodexUsageResponseParser.parse(codex, response)

        assertEquals(ProviderUsageSnapshot.STATUS_AVAILABLE, usage.status)
        assertEquals(28, usage.fiveHour?.usedPercent)
        assertEquals(1783670295, usage.fiveHour?.resetsAtEpochSeconds)
        assertEquals(16, usage.weekly?.usedPercent)
        assertEquals(2, usage.resetCount)
    }

    @Test
    fun `prefers the Codex bucket from the multi limit response`() {
        val response = JsonParser.parseString(
            """
                {
                  "rateLimits": {"primary": {"usedPercent": 99, "windowDurationMins": 300}},
                  "rateLimitsByLimitId": {
                    "codex": {
                      "primary": {"usedPercent": 21, "windowDurationMins": 300},
                      "secondary": {"usedPercent": 34, "windowDurationMins": 10080}
                    }
                  }
                }
            """.trimIndent()
        ).asJsonObject

        val usage = CodexUsageResponseParser.parse(codex, response)

        assertEquals(21, usage.fiveHour?.usedPercent)
        assertEquals(34, usage.weekly?.usedPercent)
    }

    @Test
    fun `parses Claude utilization and ISO reset timestamps`() {
        val reset = "2026-07-17T08:30:00Z"
        val usage = ClaudeUsageResponseParser.parse(
            claude,
            """
                {
                  "five_hour": {"utilization": 24.6, "resets_at": "$reset"},
                  "seven_day": {"utilization": 41.2, "resets_at": 1784277000}
                }
            """.trimIndent()
        )

        assertEquals(ProviderUsageSnapshot.STATUS_AVAILABLE, usage.status)
        assertEquals(25, usage.fiveHour?.usedPercent)
        assertEquals(Instant.parse(reset).epochSecond, usage.fiveHour?.resetsAtEpochSeconds)
        assertEquals(41, usage.weekly?.usedPercent)
        assertEquals(1784277000, usage.weekly?.resetsAtEpochSeconds)
        assertNull(usage.resetCount)
    }

    @Test
    fun `resolves Claude usage configuration from settings without changing files`() {
        val home = Files.createTempDirectory("agentdock-claude-usage")
        try {
            val configDirectory = home.resolve(".claude")
            Files.createDirectories(configDirectory)
            Files.writeString(
                configDirectory.resolve("settings.json"),
                """
                    {
                      "env": {
                        "ANTHROPIC_BASE_URL": "https://gateway.example/v1",
                        "ANTHROPIC_AUTH_TOKEN": "test-token"
                      }
                    }
                """.trimIndent()
            )

            val configuration = assertNotNull(
                ClaudeUsageConfigurationResolver(environment = emptyMap(), userHome = home).resolve()
            )

            assertEquals("https://gateway.example/api/oauth/usage", configuration.usageUri.toString())
            assertEquals("test-token", configuration.bearerToken)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rejects insecure remote Claude usage endpoints`() {
        val configuration = ClaudeUsageConfigurationResolver(
            environment = mapOf(
                "ANTHROPIC_BASE_URL" to "http://gateway.example",
                "CLAUDE_CODE_OAUTH_TOKEN" to "test-token"
            )
        ).resolve()

        assertNull(configuration)
    }

    @Test
    fun `does not send stored official Claude credentials to a custom endpoint`() {
        val home = Files.createTempDirectory("agentdock-claude-credentials")
        try {
            val configDirectory = home.resolve(".claude")
            Files.createDirectories(configDirectory)
            Files.writeString(
                configDirectory.resolve("settings.json"),
                """{"env":{"ANTHROPIC_BASE_URL":"https://gateway.example"}}"""
            )
            Files.writeString(
                configDirectory.resolve(".credentials.json"),
                """{"claudeAiOauth":{"accessToken":"official-token"}}"""
            )

            val configuration = ClaudeUsageConfigurationResolver(
                environment = emptyMap(),
                userHome = home
            ).resolve()

            assertNull(configuration)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `reads standard Claude OAuth credentials through secure storage`() {
        val home = Files.createTempDirectory("agentdock-claude-keychain")
        var secureStorageReads = 0
        try {
            val configuration = assertNotNull(
                ClaudeUsageConfigurationResolver(
                    environment = emptyMap(),
                    userHome = home,
                    secureCredentialsReader = ClaudeSecureCredentialsReader { _, customConfigDirectory ->
                        assertEquals(false, customConfigDirectory)
                        secureStorageReads += 1
                        """{"claudeAiOauth":{"accessToken":"keychain-token"}}"""
                    }
                ).resolve()
            )

            assertEquals("https://api.anthropic.com/api/oauth/usage", configuration.usageUri.toString())
            assertEquals("keychain-token", configuration.bearerToken)
            assertEquals(1, secureStorageReads)
        } finally {
            home.toFile().deleteRecursively()
        }
    }
}
