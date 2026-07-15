package com.agentdock.storage

import com.agentdock.model.CLIProvider

data class ProviderSettingsState(
    var schemaVersion: Int = 3,
    var providers: MutableList<CLIProvider> = CLIProvider.defaultProviders().toMutableList(),
    var newSessionProviderId: String = CLIProvider.CODEX_ID,
    var newSessionYolo: Boolean = false
)
