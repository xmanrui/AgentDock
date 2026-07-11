package com.agentdock.storage

import com.agentdock.model.CLIProvider

data class ProviderSettingsState(
    var schemaVersion: Int = 2,
    var providers: MutableList<CLIProvider> = CLIProvider.defaultProviders().toMutableList()
)
