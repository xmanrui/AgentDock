package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
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
                val initialContent = findTerminalContent(terminalWidget)
                val controllerRef = AtomicReference(
                    createPresentationController(initialContent, presentation.providerId, fullTitle)
                )
                val monitor = presentation.activitySource?.let { source ->
                    LocalTerminalActivityMonitor(
                        source = source,
                        onEvent = { event -> controllerRef.get()?.onActivity(event) }
                    )
                }
                monitor?.start()
                val widget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(terminalWidget)
                try {
                    widget.executeCommand(command)
                } catch (error: Throwable) {
                    monitor?.stop()
                    controllerRef.get()?.dispose()
                    throw error
                }
                val content = initialContent ?: findTerminalContent(terminalWidget)
                val finalController = controllerRef.get() ?: createPresentationController(
                    content, presentation.providerId, fullTitle
                )?.also(controllerRef::set)
                if (content != null) {
                    registerContentClosedListener(content) {
                        monitor?.stop()
                        finalController?.dispose()
                        presentation.onClosed?.invoke()
                    }
                } else {
                    monitor?.stop()
                    finalController?.dispose()
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

    private fun findTerminalContent(terminalWidget: TerminalWidget): Content? {
        val manager = TerminalToolWindowManager.getInstance(project)
        return runCatching { manager.getContainer(terminalWidget)?.content }.getOrNull()
            ?: findTerminalContentByWidget(manager, terminalWidget)
    }

    private fun findTerminalContentByWidget(
        manager: TerminalToolWindowManager,
        terminalWidget: TerminalWidget
    ): Content? {
        return manager.toolWindow.contentManager.contents.firstOrNull { content ->
            TerminalToolWindowManager.findWidgetByContent(content) == terminalWidget
        }
    }

    private fun createPresentationController(
        content: Content?,
        providerId: String?,
        fullTitle: String
    ): TerminalTaskPresentationController? {
        if (content == null) return null
        val icon = ProviderIcons.forProvider(providerId)
        if (icon == null) {
            content.description = "$fullTitle - ${TerminalTaskState.Idle.label}"
            content.toolwindowTitle = fullTitle
            return null
        }
        return TerminalTaskPresentationController(content, icon, fullTitle)
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
