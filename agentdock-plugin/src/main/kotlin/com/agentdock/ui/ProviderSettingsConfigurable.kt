package com.agentdock.ui

import com.agentdock.model.CLIProvider
import com.agentdock.service.ProviderSettingsService
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProviderSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private val fields = mutableMapOf<String, ProviderFields>()
    private val service: ProviderSettingsService
        get() = ProviderSettingsService.getInstance()

    override fun getDisplayName(): String = "AgentDock"

    override fun createComponent(): JComponent {
        val root = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
        }
        fields.clear()
        service.providers().forEachIndexed { index, provider ->
            addProviderSection(root, index, provider)
        }
        panel = root
        return root
    }

    override fun isModified(): Boolean {
        return service.providers().any { provider ->
            val field = fields[provider.id] ?: return@any false
            provider.enabled != field.enabled.isSelected ||
                provider.executable != field.executable.text ||
                provider.detectCommand != field.detectCommand.text ||
                provider.startCommandTemplate != field.startCommand.text ||
                provider.resumeCommandTemplate != field.resumeCommand.text
        }
    }

    override fun apply() {
        service.providers().forEach { provider ->
            val field = fields[provider.id] ?: return@forEach
            service.updateProvider(
                provider.copy(
                    enabled = field.enabled.isSelected,
                    executable = field.executable.text.trim(),
                    detectCommand = field.detectCommand.text.trim(),
                    startCommandTemplate = field.startCommand.text.trim(),
                    resumeCommandTemplate = field.resumeCommand.text.trim()
                )
            )
        }
    }

    override fun reset() {
        service.providers().forEach { provider ->
            fields[provider.id]?.apply {
                enabled.isSelected = provider.enabled
                executable.text = provider.executable
                detectCommand.text = provider.detectCommand
                startCommand.text = provider.startCommandTemplate
                resumeCommand.text = provider.resumeCommandTemplate
            }
        }
    }

    override fun disposeUIResources() {
        panel = null
        fields.clear()
    }

    private fun addProviderSection(root: JPanel, sectionIndex: Int, provider: CLIProvider) {
        val rowOffset = sectionIndex * 6
        addLabel(root, rowOffset, "${provider.displayName} provider")

        val providerFields = ProviderFields(
            enabled = JCheckBox("Enabled", provider.enabled),
            executable = JBTextField(provider.executable),
            detectCommand = JBTextField(provider.detectCommand),
            startCommand = JBTextField(provider.startCommandTemplate),
            resumeCommand = JBTextField(provider.resumeCommandTemplate)
        )
        fields[provider.id] = providerFields

        addField(root, rowOffset + 1, "Enabled", providerFields.enabled)
        addField(root, rowOffset + 2, "Executable", providerFields.executable)
        addField(root, rowOffset + 3, "Detect command", providerFields.detectCommand)
        addField(root, rowOffset + 4, "Start command", providerFields.startCommand)
        addField(root, rowOffset + 5, "Resume command", providerFields.resumeCommand)
    }

    private fun addLabel(root: JPanel, row: Int, text: String) {
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = java.awt.Insets(10, 0, 4, 0)
        }
        root.add(JLabel(text), constraints)
    }

    private fun addField(root: JPanel, row: Int, label: String, component: JComponent) {
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = java.awt.Insets(3, 0, 3, 10)
        }
        root.add(JLabel(label), labelConstraints)

        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = java.awt.Insets(3, 0, 3, 0)
        }
        root.add(component, fieldConstraints)
    }

    private data class ProviderFields(
        val enabled: JCheckBox,
        val executable: JBTextField,
        val detectCommand: JBTextField,
        val startCommand: JBTextField,
        val resumeCommand: JBTextField
    )
}
