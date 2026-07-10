package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult
import com.agentdock.util.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.io.File
import java.nio.file.Files

class ProviderDetectionServiceTest {
    @Test
    fun `detects direct executable path`() {
        val executable = File.createTempFile("agentdock-provider", ".sh")
        executable.writeText("#!/bin/sh\nexit 0\n")
        executable.setExecutable(true)

        val provider = CLIProvider.defaultProviders().first().copy(executable = executable.absolutePath)

        try {
            assertIs<ProviderDetectionResult.Available>(ProviderDetectionService().detect(provider))
        } finally {
            executable.delete()
        }
    }

    @Test
    fun `returns missing for unavailable direct path`() {
        val provider = CLIProvider.defaultProviders().first().copy(executable = "/not/a/real/codex")

        assertIs<ProviderDetectionResult.Missing>(ProviderDetectionService().detect(provider))
    }

    @Test
    fun `detects executable installed by nvm when IDE path does not include it`() {
        val home = Files.createTempDirectory("agentdock-provider-home").toFile()
        val executable = File(home, ".nvm/versions/node/v24.13.0/bin/codex")
        executable.parentFile.mkdirs()
        executable.writeText("#!/bin/sh\nexit 0\n")
        executable.setExecutable(true)
        val provider = CLIProvider.defaultProviders().first().copy(executable = "codex")

        try {
            val result = assertIs<ProviderDetectionResult.Available>(
                ProviderDetectionService(
                    os = OperatingSystem.Mac,
                    userHome = home,
                    pathEnvironment = ""
                ).detect(provider)
            )

            assertEquals(executable.absolutePath, result.executablePath)
        } finally {
            home.deleteRecursively()
        }
    }
}
