package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult
import kotlin.test.Test
import kotlin.test.assertIs
import java.io.File

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
}
