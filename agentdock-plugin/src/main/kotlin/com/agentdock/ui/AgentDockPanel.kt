package com.agentdock.ui

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider
import com.agentdock.notification.AgentDockNotifications
import com.agentdock.service.AgentSessionOperationResult
import com.agentdock.service.AgentSessionProjectService
import com.agentdock.service.CLIProviderRegistry
import com.agentdock.service.LocalSessionContentService
import com.agentdock.service.ProviderUsageService
import com.agentdock.util.SessionTextSanitizer
import com.agentdock.util.TimeFormatter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.Component
import java.awt.Font
import java.awt.event.HierarchyEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class AgentDockPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : Disposable {
    private val service = AgentSessionProjectService.getInstance(project)
    private val providerRegistry = CLIProviderRegistry()
    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val actionQuery: JBCefJSQuery? = browser?.let { JBCefJSQuery.create(it as JBCefBrowserBase) }
    private val removeTerminalStateListener: () -> Unit = service.addTerminalStateListener { pushState() }
    private val standardToolWindowLayout = StandardToolWindowLayout()
    private val autoRefreshTimer = Timer(AUTO_REFRESH_INTERVAL_MS) { event ->
        if (component.isShowing) {
            requestBackgroundRefresh()
        } else {
            (event.source as? Timer)?.stop()
        }
    }.apply {
        isRepeats = true
    }
    private val refreshInFlight = AtomicBoolean(false)
    private val sessionContentService = LocalSessionContentService()
    private val providerUsageService = ProviderUsageService()
    private val previewRequestVersion = AtomicLong(0)
    private val providerUsageRequestVersion = AtomicLong(0)
    private var panelShowing = false
    private var sessionCount: Int = 0
    private var lastObservedToolWindowVisible: Boolean? = null

    val component: JComponent = browser?.component ?: fallbackComponent()
    private val sessionPreviewPopup = SessionPreviewPopup(component)
    private val providerUsagePopup = ProviderUsagePopup(component)
    private val persistentRightToolWindowLayout = PersistentRightToolWindowLayout(
        content = component,
        nativeToolWindowComponent = { if (toolWindow.isDisposed) null else toolWindow.component },
        onHide = { hidePersistentToolWindow() }
    )

    init {
        project.messageBus.connect(this).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(shownToolWindow: ToolWindow) {
                    if (shownToolWindow.id == toolWindow.id) {
                        syncToolWindowVisibilityIfChanged()
                    }
                }

                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    syncToolWindowVisibilityIfChanged()
                }
            }
        )
        actionQuery?.addHandler { payload ->
            JBCefJSQuery.Response(handleAction(payload))
        }
        browser?.loadHTML(
            AgentDockHtmlRenderer.render(
                initialState = viewState(forceDiscovery = false),
                bridgeScript = actionQuery?.inject(
                    "message",
                    "response => window.AgentDock.receive(response)",
                    "(code, message) => window.AgentDock.showError(message)"
                ).orEmpty()
            )
        )
        if (browser != null) {
            installVisibilityRefreshHooks()
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            sessionContentService.warmSourceIndex()
        }
    }

    fun attachContent() {
        lastObservedToolWindowVisible = toolWindow.isVisible
        scheduleExclusiveRightToolWindowMode()
        updateToolWindowPresentation(sessionCount)
        updateRefreshSchedule()
    }

    override fun dispose() {
        previewRequestVersion.incrementAndGet()
        providerUsageRequestVersion.incrementAndGet()
        sessionPreviewPopup.dispose()
        providerUsagePopup.dispose()
        persistentRightToolWindowLayout.dispose()
        removeTerminalStateListener()
        autoRefreshTimer.stop()
        actionQuery?.dispose()
        browser?.dispose()
    }

    private fun handleAction(payload: String): String {
        return try {
            val json = parsePayload(payload)
            when (json.string("action")) {
                "open" -> json.string("id")?.let { openSession(it) }
                "pin" -> json.string("id")?.let { service.togglePin(it) }
                "refresh" -> {
                    providerUsageService.invalidate()
                    requestBackgroundRefresh()
                    return AgentDockHtmlRenderer.refreshPendingResponseJson()
                }
                "settings" -> SwingUtilities.invokeLater { openSettings() }
                "new" -> SwingUtilities.invokeLater { createSessionAndRefresh() }
                "preview-show" -> {
                    val sessionId = json.string("id") ?: return AgentDockHtmlRenderer.interactionHandledResponseJson()
                    val anchor = json.previewAnchor()
                        ?: return AgentDockHtmlRenderer.interactionHandledResponseJson()
                    requestSessionPreview(sessionId, anchor)
                    return AgentDockHtmlRenderer.interactionHandledResponseJson()
                }
                "preview-hide" -> {
                    hideSessionPreview(immediate = json.boolean("immediate") == true)
                    return AgentDockHtmlRenderer.interactionHandledResponseJson()
                }
                "provider-usage-show" -> {
                    val providerId = json.string("providerId")
                        ?: return AgentDockHtmlRenderer.interactionHandledResponseJson()
                    val anchor = json.providerUsageAnchor()
                        ?: return AgentDockHtmlRenderer.interactionHandledResponseJson()
                    requestProviderUsage(providerId, anchor)
                    return AgentDockHtmlRenderer.interactionHandledResponseJson()
                }
                "provider-usage-hide" -> {
                    hideProviderUsage()
                    return AgentDockHtmlRenderer.interactionHandledResponseJson()
                }
            }
            actionResponse()
        } catch (error: Exception) {
            actionResponse(error = error.message ?: "AgentDock action failed")
        }
    }

    private fun openSession(sessionId: String) {
        hideSessionPreview(immediate = true)
        runOnEdt {
            handleResult(service.resumeSession(sessionId))
            scheduleExclusiveRightToolWindowMode()
        }
    }

    private fun createSessionAndRefresh() {
        hideSessionPreview(immediate = true)
        val providers = providerRegistry.listEnabledProviders()
        val dialog = NewSessionDialog(project, providers)
        if (!dialog.showAndGet()) return

        val result = service.createAndLaunchSession(
            providerId = dialog.providerId,
            name = dialog.sessionName,
            cwd = dialog.cwd,
            summary = dialog.summary,
            providerSessionId = dialog.providerSessionId
        )
        handleResult(result)
        scheduleExclusiveRightToolWindowMode()
        pushState()
    }

    private fun openSettings() {
        hideSessionPreview(immediate = true)
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "AgentDock")
    }

    private fun requestSessionPreview(sessionId: String, anchor: SessionPreviewAnchor) {
        val session = service.findSession(sessionId)?.copy() ?: return
        val providerName = providerRegistry.getProvider(session.providerId)?.displayName ?: session.providerId
        val requestVersion = previewRequestVersion.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            sessionPreviewPopup.hideImmediately()
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val preview = sessionContentService.load(session)
            ApplicationManager.getApplication().invokeLater {
                if (previewRequestVersion.get() != requestVersion || !component.isShowing) {
                    return@invokeLater
                }
                sessionPreviewPopup.show(session, providerName, preview, anchor)
            }
        }
    }

    private fun hideSessionPreview(immediate: Boolean) {
        previewRequestVersion.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (immediate) {
                sessionPreviewPopup.hideImmediately()
            } else {
                sessionPreviewPopup.requestHide()
            }
        }
    }

    private fun requestProviderUsage(providerId: String, anchor: ProviderUsageAnchor) {
        val provider = providerRegistry.getProvider(providerId) ?: return
        val requestVersion = providerUsageRequestVersion.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (providerUsageRequestVersion.get() == requestVersion && component.isShowing) {
                providerUsagePopup.showLoading(provider, anchor)
            }
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val usage = providerUsageService.load(provider)
            ApplicationManager.getApplication().invokeLater {
                if (providerUsageRequestVersion.get() == requestVersion && component.isShowing) {
                    providerUsagePopup.showUsage(usage, anchor)
                }
            }
        }
    }

    private fun hideProviderUsage() {
        providerUsageRequestVersion.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            providerUsagePopup.hide()
        }
    }

    private fun preloadProviderUsage() {
        providerRegistry.listEnabledProviders()
            .filter { provider ->
                provider.id == CLIProvider.CODEX_ID || provider.id == CLIProvider.CLAUDE_CODE_ID
            }
            .forEach { provider ->
                ApplicationManager.getApplication().executeOnPooledThread {
                    providerUsageService.load(provider)
                }
            }
    }

    private fun pushState(forceDiscovery: Boolean = false) {
        val response = actionResponse(forceDiscovery = forceDiscovery)
        val script = "window.AgentDock && window.AgentDock.receive(${quoteJs(response)});"
        ApplicationManager.getApplication().invokeLater {
            browser?.cefBrowser?.executeJavaScript(script, browser.cefBrowser.url, 0)
        }
    }

    private fun pushError(error: Throwable) {
        val response = AgentDockHtmlRenderer.errorResponseJson(error.message ?: "AgentDock refresh failed")
        val script = "window.AgentDock && window.AgentDock.receive(${quoteJs(response)});"
        ApplicationManager.getApplication().invokeLater {
            browser?.cefBrowser?.executeJavaScript(script, browser.cefBrowser.url, 0)
        }
    }

    private fun requestBackgroundRefresh() {
        preloadProviderUsage()
        if (!refreshInFlight.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                pushState(forceDiscovery = true)
            } catch (error: Throwable) {
                pushError(error)
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    private fun installVisibilityRefreshHooks() {
        component.addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                updateRefreshSchedule()
            }
        }
        component.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(event: ComponentEvent) {
                updateRefreshSchedule()
            }

            override fun componentHidden(event: ComponentEvent) {
                updateRefreshSchedule()
            }
        })
        SwingUtilities.invokeLater {
            updateRefreshSchedule()
        }
    }

    private fun updateRefreshSchedule() {
        val showing = component.isShowing
        if (showing) {
            if (!autoRefreshTimer.isRunning) {
                autoRefreshTimer.start()
            }
            if (!panelShowing) {
                requestBackgroundRefresh()
                scheduleExclusiveRightToolWindowMode()
            }
        } else {
            autoRefreshTimer.stop()
            hideSessionPreview(immediate = true)
            hideProviderUsage()
        }
        panelShowing = showing
    }

    private fun actionResponse(
        query: String? = null,
        error: String? = null,
        forceDiscovery: Boolean = false
    ): String {
        return AgentDockHtmlRenderer.actionResponseJson(
            AgentDockHtmlRenderer.ActionResponse(
                state = viewState(forceDiscovery = forceDiscovery),
                query = query,
                error = error
            )
        )
    }

    private fun viewState(forceDiscovery: Boolean): AgentDockHtmlRenderer.ViewState {
        if (forceDiscovery) {
            service.syncDiscoveredSessions(force = true)
        }
        val providerList = providerRegistry.listProviders()
        val providers = providerList.associateBy { it.id }
        val sessions = service.listSessions(includeArchived = true, discover = false)
            .map { session -> session.toViewItem(providers[session.providerId]) }
        val count = service.listSessions(includeArchived = false, discover = false).size
        updateToolWindowPresentation(count)
        return AgentDockHtmlRenderer.ViewState(
            sessions = sessions,
            providers = providerList.map { provider ->
                AgentDockHtmlRenderer.ProviderItem(
                    id = provider.id,
                    name = provider.displayName
                )
            },
            count = count
        )
    }

    private fun AgentSession.toViewItem(provider: CLIProvider?): AgentDockHtmlRenderer.SessionItem {
        val displayStatus = if (archived) AgentSessionStatus.Archived else status
        val title = SessionTextSanitizer.title(name, fallbackName(providerSessionId))
        val summary = SessionTextSanitizer.summary(summary)
            .ifBlank { SessionTextSanitizer.summary(name) }
            .ifBlank { title }
        return AgentDockHtmlRenderer.SessionItem(
            id = id,
            providerId = providerId,
            providerName = provider?.displayName ?: providerId,
            title = title,
            summary = summary,
            statusKey = displayStatus.statusKey(),
            statusLabel = displayStatus.statusLabel(),
            terminalOpen = service.isTerminalOpen(id),
            updatedLabel = TimeFormatter.relative(updatedAt),
            pinned = pinned,
            archived = archived
        )
    }

    private fun updateToolWindowPresentation(count: Int) {
        sessionCount = count
        val title = "AgentDock"
        runOnEdt {
            toolWindow.setStripeTitle(title)
            toolWindow.component.toolTipText = null
            toolWindow.component.accessibleContext?.accessibleDescription = title
            installTitleLabel(title)
        }
        reapplyTitleLabel(title)
    }

    private fun reapplyTitleLabel(title: String) {
        ApplicationManager.getApplication().invokeLater {
            installTitleLabel(title)
        }
        listOf(250, 1_000, 2_500, 5_000).forEach { delay ->
            Timer(delay) {
                installTitleLabel(title)
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun installTitleLabel(title: String) {
        val root = toolWindow.component.topLevelAncestor ?: toolWindow.component
        applyTitleToTitleLabels(root, title)
    }

    private fun applyTitleToTitleLabels(component: Component, title: String) {
        val accessibleName = component.accessibleContext?.accessibleName
        if (component is JLabel && isToolWindowTitleLabel(component, title, accessibleName)) {
            component.text = title
            component.accessibleContext?.accessibleName = title
            component.accessibleContext?.accessibleDescription = title
            component.toolTipText = null
        }
        if (component is java.awt.Container) {
            component.components.forEach { child ->
                applyTitleToTitleLabels(child, title)
            }
        }
    }

    private fun isToolWindowTitleLabel(label: JLabel, title: String, accessibleName: String?): Boolean {
        return label.text == title || accessibleName == title
    }

    private fun handleResult(result: AgentSessionOperationResult) {
        if (result is AgentSessionOperationResult.Failure) {
            AgentDockNotifications.warning(project, "AgentDock", result.message)
        }
    }

    private fun scheduleExclusiveRightToolWindowMode() {
        ApplicationManager.getApplication().invokeLater {
            tryApplyExclusiveRightToolWindowMode()
        }
        listOf(250, 1_000).forEach { delay ->
            Timer(delay) {
                tryApplyExclusiveRightToolWindowMode()
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun tryApplyExclusiveRightToolWindowMode() {
        runCatching {
            if (toolWindow.isDisposed) return
            standardToolWindowLayout.enable()
            if (toolWindow.anchor != ToolWindowAnchor.RIGHT) {
                toolWindow.setAnchor(ToolWindowAnchor.RIGHT, TOOL_WINDOW_CALLBACK)
            }
            if (toolWindow.type != ToolWindowType.DOCKED) {
                toolWindow.setType(ToolWindowType.DOCKED, TOOL_WINDOW_CALLBACK)
            }
            if (toolWindow.isAutoHide) {
                toolWindow.setAutoHide(false)
            }
            if (toolWindow.isSplitMode) {
                toolWindow.setSplitMode(false, TOOL_WINDOW_CALLBACK)
            }
            hideOtherRightToolWindows()
            if (toolWindow.isVisible) {
                persistentRightToolWindowLayout.show()
            }
        }
    }

    private fun scheduleToolWindowVisibilitySync() {
        ApplicationManager.getApplication().invokeLater {
            if (toolWindow.isDisposed) return@invokeLater
            if (toolWindow.isVisible) {
                scheduleExclusiveRightToolWindowMode()
            } else {
                persistentRightToolWindowLayout.hide()
            }
        }
    }

    private fun syncToolWindowVisibilityIfChanged() {
        if (toolWindow.isDisposed) return
        val visible = toolWindow.isVisible
        if (lastObservedToolWindowVisible == visible) return
        lastObservedToolWindowVisible = visible
        scheduleToolWindowVisibilitySync()
    }

    private fun hidePersistentToolWindow() {
        hideSessionPreview(immediate = true)
        if (toolWindow.isDisposed || !toolWindow.isVisible) {
            persistentRightToolWindowLayout.hide()
            return
        }
        toolWindow.hide(Runnable { persistentRightToolWindowLayout.hide() })
    }

    private fun hideOtherRightToolWindows() {
        val manager = ToolWindowManager.getInstance(project)
        manager.toolWindowIds
            .asSequence()
            .filter { id -> id != toolWindow.id }
            .mapNotNull { id -> manager.getToolWindow(id) }
            .filter { other -> other.anchor == ToolWindowAnchor.RIGHT && other.isVisible }
            .forEach { other -> other.hide(TOOL_WINDOW_CALLBACK) }
    }

    private fun parsePayload(payload: String): JsonObject {
        return JsonParser.parseString(payload).asJsonObject
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) value.asBoolean else null
    }

    private fun JsonObject.int(name: String): Int? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asInt else null
    }

    private fun JsonObject.previewAnchor(): SessionPreviewAnchor? {
        return SessionPreviewAnchor(
            left = int("left") ?: return null,
            top = int("top") ?: return null,
            width = int("width") ?: return null,
            height = int("height") ?: return null
        )
    }

    private fun JsonObject.providerUsageAnchor(): ProviderUsageAnchor? {
        return ProviderUsageAnchor(
            left = int("left") ?: return null,
            top = int("top") ?: return null,
            width = int("width") ?: return null,
            height = int("height") ?: return null,
            usePointer = boolean("usePointer") == true
        )
    }

    private fun AgentSessionStatus.statusKey(): String {
        return when (this) {
            AgentSessionStatus.Active -> "active"
            AgentSessionStatus.Restorable -> "restorable"
            AgentSessionStatus.MissingCli -> "missing-cli"
            AgentSessionStatus.Error -> "error"
            AgentSessionStatus.Archived -> "archived"
        }
    }

    private fun AgentSessionStatus.statusLabel(): String {
        return when (this) {
            AgentSessionStatus.Active -> "Active"
            AgentSessionStatus.Restorable -> "Restorable"
            AgentSessionStatus.MissingCli -> "Missing CLI"
            AgentSessionStatus.Error -> "Error"
            AgentSessionStatus.Archived -> "Archived"
        }
    }

    private fun fallbackName(providerSessionId: String?): String {
        return providerSessionId?.take(8)?.let { "Agent session $it" } ?: "Agent session"
    }

    private fun fallbackComponent(): JComponent {
        return JPanel(BorderLayout()).apply {
            background = Color(0x1B1F1B)
            add(
                JLabel("AgentDock").apply {
                    foreground = Color(0xEEF2EC)
                    font = font.deriveFont(Font.BOLD, font.size2D + 2f)
                    border = javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12)
                },
                BorderLayout.NORTH
            )
        }
    }

    private fun quoteJs(value: String): String {
        return "'" + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "'"
    }

    private fun <T> runOnEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: T? = null
        var failure: Throwable? = null
        ApplicationManager.getApplication().invokeAndWait {
            try {
                result = block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 180_000
        private val TOOL_WINDOW_CALLBACK = Runnable {}
    }
}
