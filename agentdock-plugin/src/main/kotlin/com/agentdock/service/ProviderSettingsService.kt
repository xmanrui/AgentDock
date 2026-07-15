package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.storage.ProviderSettingsState
import com.agentdock.storage.StateMigration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "AgentDockProviderSettings", storages = [Storage("agentDockProviderSettings.xml")])
class ProviderSettingsService : PersistentStateComponent<ProviderSettingsState> {
    private var myState = ProviderSettingsState()

    override fun getState(): ProviderSettingsState = StateMigration.migrateProviderSettings(myState)

    override fun loadState(state: ProviderSettingsState) {
        myState = StateMigration.migrateProviderSettings(state)
    }

    fun providers(): List<CLIProvider> = getState().providers.map { it.copy() }

    fun provider(providerId: String): CLIProvider? = providers().firstOrNull { it.id == providerId }

    fun newSessionPreference(enabledProviders: List<CLIProvider> = providers().filter { it.enabled }): NewSessionPreference? {
        val configuredProvider = enabledProviders.firstOrNull { it.id == getState().newSessionProviderId }
        if (configuredProvider != null) {
            return NewSessionPreference(configuredProvider.id, getState().newSessionYolo)
        }
        val fallbackProvider = enabledProviders.firstOrNull() ?: return null
        updateNewSessionPreference(fallbackProvider.id, yolo = false)
        return NewSessionPreference(fallbackProvider.id, yolo = false)
    }

    fun updateNewSessionPreference(providerId: String, yolo: Boolean) {
        getState().newSessionProviderId = providerId
        getState().newSessionYolo = yolo
    }

    fun updateProvider(updated: CLIProvider) {
        val providers = getState().providers
        val index = providers.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            providers[index] = updated
        } else {
            providers.add(updated)
        }
    }

    fun resetDefaults() {
        myState = ProviderSettingsState()
    }

    companion object {
        fun getInstance(): ProviderSettingsService =
            ApplicationManager.getApplication().getService(ProviderSettingsService::class.java)
    }
}

data class NewSessionPreference(
    val providerId: String,
    val yolo: Boolean
)
