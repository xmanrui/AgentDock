package com.agentdock.model

data class ProviderUsageWindow(
    val usedPercent: Int,
    val resetsAtEpochSeconds: Long? = null
)

data class ProviderUsageSnapshot(
    val providerId: String,
    val providerName: String,
    val status: String,
    val message: String? = null,
    val fiveHour: ProviderUsageWindow? = null,
    val weekly: ProviderUsageWindow? = null,
    val resetCount: Long? = null
) {
    companion object {
        const val STATUS_AVAILABLE = "available"
        const val STATUS_UNAVAILABLE = "unavailable"
        const val STATUS_UNAUTHENTICATED = "unauthenticated"

        fun unavailable(provider: CLIProvider, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = provider.id,
                providerName = provider.displayName,
                status = STATUS_UNAVAILABLE,
                message = message
            )
        }

        fun unauthenticated(provider: CLIProvider, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = provider.id,
                providerName = provider.displayName,
                status = STATUS_UNAUTHENTICATED,
                message = message
            )
        }
    }
}
