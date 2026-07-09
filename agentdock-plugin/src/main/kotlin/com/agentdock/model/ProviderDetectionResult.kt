package com.agentdock.model

sealed class ProviderDetectionResult {
    data class Available(val executablePath: String) : ProviderDetectionResult()
    data class Missing(val reason: String) : ProviderDetectionResult()
    data class Disabled(val reason: String = "Provider is disabled") : ProviderDetectionResult()
}
