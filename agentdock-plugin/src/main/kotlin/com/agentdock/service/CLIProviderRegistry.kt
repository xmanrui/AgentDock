package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult

class CLIProviderRegistry(
    private val settingsService: ProviderSettingsService = ProviderSettingsService.getInstance(),
    private val detectionService: ProviderDetectionService = ProviderDetectionService()
) {
    fun listEnabledProviders(): List<CLIProvider> = settingsService.providers().filter { it.enabled }

    fun listProviders(): List<CLIProvider> = settingsService.providers()

    fun getProvider(providerId: String): CLIProvider? = settingsService.provider(providerId)

    fun detect(providerId: String): ProviderDetectionResult {
        val provider = getProvider(providerId)
            ?: return ProviderDetectionResult.Missing("Unknown provider: $providerId")
        return detectionService.detect(provider)
    }
}
