package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.Timer

class JetBrainsTerminalLauncher(private val project: Project) : TerminalLauncher {
    override fun launch(command: String, cwd: String, presentation: TerminalTabPresentation): TerminalLaunchResult {
        if (cwd.isBlank() || !File(cwd).isDirectory) {
            return TerminalLaunchResult.Failed("Working directory does not exist: $cwd")
        }

        val fullTitle = TerminalTabTitleFormatter.fullTitle(presentation.title)
        val tabTitle = TerminalTabTitleFormatter.displayTitle(presentation.title)
        return try {
            runOnEdt {
                val terminalWidget = createShellWidget(cwd, tabTitle)
                applyTabPresentation(terminalWidget, presentation.providerId, fullTitle)
                val widget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(terminalWidget)
                widget.executeCommand(command)
                val content = applyTabPresentation(terminalWidget, presentation.providerId, fullTitle)
                if (content != null && presentation.onClosed != null) {
                    registerContentClosedListener(content, presentation.onClosed)
                }
            }
            TerminalLaunchResult.Sent("Command sent to terminal tab: $fullTitle")
        } catch (error: Throwable) {
            clipboardFallback(command, "Terminal command execution failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun clipboardFallback(command: String, reason: String): TerminalLaunchResult {
        ClipboardCommandFallback.copy(command)
        val terminal = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
        return if (terminal != null) {
            terminal.activate(null)
            TerminalLaunchResult.ClipboardFallback(
                "$reason. Terminal opened and command copied. Paste it to continue: $command"
            )
        } else {
            TerminalLaunchResult.ClipboardFallback(
                "$reason. Command copied, but Terminal tool window is unavailable: $command"
            )
        }
    }

    private fun createShellWidget(cwd: String, tabTitle: String): TerminalWidget {
        val manager = TerminalToolWindowManager.getInstance(project)
        val method = manager.javaClass.getMethod(
            "createShellWidget",
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        return method.invoke(manager, cwd, tabTitle, true, true) as TerminalWidget
    }

    private fun applyTabPresentation(terminalWidget: TerminalWidget, providerId: String?, fullTitle: String): Content? {
        val manager = TerminalToolWindowManager.getInstance(project)
        val content = runCatching { manager.getContainer(terminalWidget)?.content }.getOrNull()
            ?: findTerminalContent(manager, terminalWidget)
            ?: return null

        applyContentPresentation(content, providerId, fullTitle)
        reapplyContentPresentation(content, providerId, fullTitle)
        return content
    }

    private fun applyContentPresentation(content: Content, providerId: String?, fullTitle: String) {
        content.description = fullTitle
        content.toolwindowTitle = fullTitle

        val icon = ProviderIcons.forProvider(providerId) ?: return
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        content.icon = icon
        content.popupIcon = icon
    }

    private fun reapplyContentPresentation(content: Content, providerId: String?, fullTitle: String) {
        ApplicationManager.getApplication().invokeLater {
            if (content.isValid) {
                applyContentPresentation(content, providerId, fullTitle)
            }
        }
        listOf(250, 1_000).forEach { delay ->
            Timer(delay) {
                if (content.isValid) {
                    applyContentPresentation(content, providerId, fullTitle)
                }
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun findTerminalContent(manager: TerminalToolWindowManager, terminalWidget: TerminalWidget): Content? {
        return manager.toolWindow.contentManager.contents.firstOrNull { content ->
            TerminalToolWindowManager.findWidgetByContent(content) == terminalWidget
        }
    }

    private fun registerContentClosedListener(content: Content, onClosed: () -> Unit) {
        val manager = content.manager ?: return
        val notified = AtomicBoolean(false)
        val listener = object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content != content) return
                manager.removeContentManagerListener(this)
                if (notified.compareAndSet(false, true)) {
                    onClosed()
                }
            }
        }
        manager.addContentManagerListener(listener)
        if (!content.isValid && notified.compareAndSet(false, true)) {
            manager.removeContentManagerListener(listener)
            onClosed()
        }
    }

    private fun <T> runOnEdt(action: () -> T): T {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return action()
        }

        val result = AtomicReference<Result<T>>()
        application.invokeAndWait {
            result.set(runCatching(action))
        }
        return result.get().getOrThrow()
    }

    private object ProviderIcons {
        private val codex: Icon = IconLoader.getIcon("/icons/codex.svg", ProviderIcons::class.java)
        private val claude: Icon = IconLoader.getIcon("/icons/claude.svg", ProviderIcons::class.java)

        fun forProvider(providerId: String?): Icon? {
            return when (providerId) {
                CLIProvider.CODEX_ID -> codex
                CLIProvider.CLAUDE_CODE_ID -> claude
                else -> null
            }
        }
    }
}
