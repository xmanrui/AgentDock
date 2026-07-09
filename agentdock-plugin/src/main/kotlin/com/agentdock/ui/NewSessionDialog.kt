package com.agentdock.ui

import com.agentdock.model.CLIProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class NewSessionDialog(project: Project, providers: List<CLIProvider>) : DialogWrapper(project) {
    private val providerItems = providers.map { ProviderItem(it) }
    private val providerCombo = JComboBox(providerItems.toTypedArray())
    private val nameField = JBTextField()
    private val cwdField = JBTextField(project.basePath.orEmpty())
    private val summaryField = JBTextField()
    private val providerSessionIdField = JBTextField()

    val providerId: String
        get() = (providerCombo.selectedItem as ProviderItem).provider.id

    val sessionName: String
        get() = nameField.text.trim()

    val cwd: String
        get() = cwdField.text.trim()

    val summary: String
        get() = summaryField.text.trim()

    val providerSessionId: String?
        get() = providerSessionIdField.text.trim().takeIf { it.isNotBlank() }

    init {
        title = "New Agent Session"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = java.awt.Dimension(460, 160)

        addRow(panel, 0, "Provider", providerCombo)
        addRow(panel, 1, "Name", nameField)
        addRow(panel, 2, "Working directory", cwdField)
        addRow(panel, 3, "Summary", summaryField)
        addRow(panel, 4, "Provider session id", providerSessionIdField)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        return when {
            providerItems.isEmpty() -> ValidationInfo("No enabled provider is configured", providerCombo)
            sessionName.isBlank() -> ValidationInfo("Session name is required", nameField)
            cwd.isBlank() -> ValidationInfo("Working directory is required", cwdField)
            else -> null
        }
    }

    private fun addRow(panel: JPanel, row: Int, label: String, component: JComponent) {
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = java.awt.Insets(4, 0, 4, 10)
        }
        panel.add(JLabel(label), labelConstraints)

        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = java.awt.Insets(4, 0, 4, 0)
        }
        panel.add(component, fieldConstraints)
    }

    private data class ProviderItem(val provider: CLIProvider) {
        override fun toString(): String = provider.displayName
    }
}
