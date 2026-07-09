package com.agentdock.terminal

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

object ClipboardCommandFallback {
    fun copy(command: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(command))
    }
}
