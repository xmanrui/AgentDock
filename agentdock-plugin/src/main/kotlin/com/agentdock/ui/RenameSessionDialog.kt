package com.agentdock.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import javax.swing.JComponent
import javax.swing.JPanel

class RenameSessionDialog(project: Project, currentName: String) : DialogWrapper(project) {
    private val nameField = JBTextField(currentName)

    val newName: String
        get() = nameField.text.trim()

    init {
        title = "Rename Agent Session"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(java.awt.BorderLayout()).apply {
            preferredSize = java.awt.Dimension(360, 40)
            add(nameField, java.awt.BorderLayout.CENTER)
        }
    }

    override fun doValidate(): ValidationInfo? {
        return if (newName.isBlank()) ValidationInfo("Session name is required", nameField) else null
    }
}
