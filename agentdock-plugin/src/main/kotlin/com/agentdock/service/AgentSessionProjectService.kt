package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderCommandContext
import com.agentdock.model.ProviderDetectionResult
import com.agentdock.notification.AgentDockNotifications
import com.agentdock.storage.AgentDockProjectState
import com.agentdock.storage.AgentSessionRepository
import com.agentdock.storage.SessionFilter
import com.agentdock.storage.StateMigration
import com.agentdock.terminal.CommandRenderResult
import com.agentdock.terminal.CommandRenderer
import com.agentdock.terminal.JetBrainsTerminalLauncher
import com.agentdock.terminal.TerminalCommandExitMarker
import com.agentdock.terminal.TerminalLaunchResult
import com.agentdock.terminal.TerminalLauncher
import com.agentdock.terminal.TerminalTabPresentation
import com.agentdock.terminal.TerminalActivitySource
import com.agentdock.util.IdGenerator
import com.agentdock.util.OperatingSystem
import com.agentdock.util.ProjectIdentity
import com.agentdock.util.SessionTextSanitizer
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Timer

@Service(Service.Level.PROJECT)
@State(name = "AgentDockProjectState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AgentSessionProjectService(private val project: Project) : PersistentStateComponent<AgentDockProjectState> {
    private var myState = AgentDockProjectState()
    private var lastDiscoveryAt = 0L
    private val terminalOpenTokensBySessionId: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val exitMarkerTimersByToken: MutableMap<String, Timer> = mutableMapOf()
    private val terminalStateListeners = CopyOnWriteArrayList<() -> Unit>()
    private val sessionContentService = LocalSessionContentService()

    private val repository: AgentSessionRepository
        get() = AgentSessionRepository(getState())

    override fun getState(): AgentDockProjectState = StateMigration.migrateProjectState(myState)

    override fun loadState(state: AgentDockProjectState) {
        myState = StateMigration.migrateProjectState(state)
        resetTransientTerminalState()
    }

    fun listSessions(
        query: String = "",
        providerId: String? = null,
        status: AgentSessionStatus? = null,
        includeArchived: Boolean = false,
        discover: Boolean = true
    ): List<AgentSession> {
        if (discover) {
            syncDiscoveredSessions()
        }
        val registry = CLIProviderRegistry()
        return SessionFilter.filter(
            sessions = getState().sessions,
            providers = registry.listProviders(),
            query = query,
            providerId = providerId,
            status = status,
            includeArchived = includeArchived
        )
    }

    fun findSession(sessionId: String): AgentSession? = repository.find(sessionId)

    fun isTerminalOpen(sessionId: String): Boolean = synchronized(terminalOpenTokensBySessionId) {
        terminalOpenTokensBySessionId[sessionId]?.isNotEmpty() == true
    }

    fun addTerminalStateListener(listener: () -> Unit): () -> Unit {
        terminalStateListeners.add(listener)
        return { terminalStateListeners.remove(listener) }
    }

    fun syncDiscoveredSessions(force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        if (!force && now - lastDiscoveryAt < DISCOVERY_THROTTLE_MS) {
            return 0
        }
        lastDiscoveryAt = now

        val projectPath = project.basePath?.takeIf { it.isNotBlank() } ?: return 0
        val discovered = LocalSessionDiscoveryService().discover(projectPath)
        discovered.forEach { discoveredSession ->
            val existing = repository.find(discoveredSession.id)
            if (existing == null) {
                repository.add(discoveredSession)
            } else {
                mergeDiscoveredSession(existing, discoveredSession)
                repository.update(existing)
            }
        }
        return discovered.size
    }

    fun createAndLaunchSession(
        providerId: String,
        name: String,
        cwd: String,
        summary: String = "",
        providerSessionId: String? = null,
        terminalLauncher: TerminalLauncher = JetBrainsTerminalLauncher(project)
    ): AgentSessionOperationResult {
        val registry = CLIProviderRegistry()
        val provider = registry.getProvider(providerId)
            ?: return AgentSessionOperationResult.Failure(null, "Unknown provider: $providerId")

        val now = System.currentTimeMillis()
        val session = AgentSession(
            id = IdGenerator.sessionId(now),
            projectId = ProjectIdentity.idFor(project),
            projectPath = project.basePath.orEmpty(),
            name = name.ifBlank { "Untitled ${provider.displayName} Session" },
            providerId = provider.id,
            status = AgentSessionStatus.Restorable,
            cwd = cwd.ifBlank { project.basePath ?: System.getProperty("user.home") },
            providerSessionId = providerSessionId?.takeIf { it.isNotBlank() },
            summary = summary,
            createdAt = now,
            updatedAt = now
        )

        repository.add(session)

        val detection = registry.detect(providerId)
        if (detection !is ProviderDetectionResult.Available) {
            session.status = AgentSessionStatus.MissingCli
            clearTerminalOpenState(session.id)
            session.lastError = detectionMessage(detection)
            repository.update(session)
            AgentDockNotifications.warning(project, "${provider.displayName} missing", session.lastError.orEmpty())
            return AgentSessionOperationResult.Failure(session, session.lastError.orEmpty())
        }

        return launchSessionCommand(
            session,
            provider.copy(executable = detection.executablePath),
            provider.startCommandTemplate,
            terminalLauncher
        )
    }

    fun resumeSession(
        sessionId: String,
        terminalLauncher: TerminalLauncher = JetBrainsTerminalLauncher(project)
    ): AgentSessionOperationResult {
        val session = repository.find(sessionId)
            ?: return AgentSessionOperationResult.Failure(null, "Session not found: $sessionId")
        val registry = CLIProviderRegistry()
        val provider = registry.getProvider(session.providerId)
            ?: return markFailure(session, "Unknown provider: ${session.providerId}")

        val detection = registry.detect(provider.id)
        if (detection !is ProviderDetectionResult.Available) {
            session.status = AgentSessionStatus.MissingCli
            clearTerminalOpenState(session.id)
            session.lastError = detectionMessage(detection)
            repository.update(session)
            AgentDockNotifications.warning(project, "${provider.displayName} missing", session.lastError.orEmpty())
            return AgentSessionOperationResult.Failure(session, session.lastError.orEmpty())
        }

        return launchSessionCommand(
            session,
            provider.copy(executable = detection.executablePath),
            provider.resumeCommandTemplate,
            terminalLauncher
        )
    }

    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = repository.find(sessionId) ?: return false
        session.name = newName.ifBlank { session.name }
        session.updatedAt = System.currentTimeMillis()
        repository.update(session)
        return true
    }

    fun togglePin(sessionId: String): Boolean {
        val session = repository.find(sessionId) ?: return false
        session.pinned = !session.pinned
        session.updatedAt = System.currentTimeMillis()
        repository.update(session)
        return true
    }

    fun toggleArchive(sessionId: String): Boolean {
        val session = repository.find(sessionId) ?: return false
        session.archived = !session.archived
        session.status = if (session.archived) AgentSessionStatus.Archived else AgentSessionStatus.Restorable
        if (session.archived) {
            clearTerminalOpenState(session.id)
        }
        session.updatedAt = System.currentTimeMillis()
        repository.update(session)
        return true
    }

    private fun launchSessionCommand(
        session: AgentSession,
        provider: CLIProvider,
        commandTemplate: String,
        terminalLauncher: TerminalLauncher
    ): AgentSessionOperationResult {
        if (!File(session.cwd).isDirectory) {
            return markFailure(session, "Working directory does not exist: ${session.cwd}")
        }

        val context = ProviderCommandContext(
            provider = provider,
            session = session,
            projectPath = project.basePath.orEmpty(),
            shell = System.getenv("SHELL").orEmpty(),
            os = OperatingSystem.current()
        )

        val command = when (val result = CommandRenderer().render(commandTemplate, context)) {
            is CommandRenderResult.Success -> result.command
            is CommandRenderResult.MissingVariable -> {
                val message = "Missing command template variable: ${result.variable}"
                session.lastError = message
                repository.update(session)
                AgentDockNotifications.warning(project, "Cannot render command", message)
                return AgentSessionOperationResult.Failure(session, message)
            }
        }

        val terminalToken = UUID.randomUUID().toString()
        val historyFilePath = resolveHistoryFilePath(session)
        val exitMarkerFile = TerminalCommandExitMarker.markerFile(terminalToken)
            .takeIf { TerminalCommandExitMarker.supports(context.os) }
            ?.also { marker ->
                marker.parentFile?.mkdirs()
                marker.delete()
            }
        val launchCommand = exitMarkerFile
            ?.let { TerminalCommandExitMarker.wrap(command, it, context.os) }
            ?: command
        val terminalResult = terminalLauncher.launch(
            launchCommand,
            session.cwd,
            TerminalTabPresentation(
                title = SessionTextSanitizer.title(session.name, "Agent session"),
                providerId = provider.id,
                activitySource = historyFilePath.takeIf { it.isNotBlank() }?.let {
                    TerminalActivitySource(
                        providerId = provider.id,
                        historyFilePath = it
                    )
                },
                onClosed = { markTerminalClosed(session.id, terminalToken) }
            )
        )
        return when (terminalResult) {
            is TerminalLaunchResult.Failed -> markFailure(session, terminalResult.message)
            is TerminalLaunchResult.ClipboardFallback -> {
                clearTerminalOpenState(session.id)
                session.status = if (session.archived) AgentSessionStatus.Archived else AgentSessionStatus.Restorable
                session.lastError = null
                session.updatedAt = System.currentTimeMillis()
                repository.update(session)
                AgentDockNotifications.info(project, "Command copied", terminalResult.message)
                AgentSessionOperationResult.Success(session, terminalResult)
            }
            is TerminalLaunchResult.Sent -> {
                markTerminalOpen(session.id, terminalToken)
                if (exitMarkerFile != null) {
                    watchExitMarker(session.id, terminalToken, exitMarkerFile)
                }
                session.status = AgentSessionStatus.Active
                session.lastError = null
                session.updatedAt = System.currentTimeMillis()
                repository.update(session)
                AgentSessionOperationResult.Success(session, terminalResult)
            }
        }
    }

    private fun markFailure(session: AgentSession, message: String): AgentSessionOperationResult.Failure {
        clearTerminalOpenState(session.id)
        session.status = AgentSessionStatus.Error
        session.lastError = message
        session.updatedAt = System.currentTimeMillis()
        repository.update(session)
        AgentDockNotifications.error(project, "AgentDock error", message)
        return AgentSessionOperationResult.Failure(session, message)
    }

    private fun resolveHistoryFilePath(session: AgentSession): String {
        if (session.historyFilePath.isNotBlank()) return session.historyFilePath
        val historyFilePath = sessionContentService.locateHistoryFile(session)?.absolutePath.orEmpty()
        if (historyFilePath.isNotBlank()) {
            session.historyFilePath = historyFilePath
            repository.update(session)
        }
        return historyFilePath
    }

    private fun detectionMessage(result: ProviderDetectionResult): String {
        return when (result) {
            is ProviderDetectionResult.Available -> "Available: ${result.executablePath}"
            is ProviderDetectionResult.Disabled -> result.reason
            is ProviderDetectionResult.Missing -> result.reason
        }
    }

    private fun mergeDiscoveredSession(existing: AgentSession, discovered: AgentSession) {
        existing.projectId = discovered.projectId
        existing.projectPath = discovered.projectPath
        if (existing.name.isBlank() ||
            existing.name.startsWith("Codex session ") ||
            existing.name.startsWith("Claude Code session ") ||
            SessionTextSanitizer.isNoisy(existing.name)
        ) {
            existing.name = discovered.name
        }
        existing.providerId = discovered.providerId
        if (!existing.archived && !isTerminalOpen(existing.id)) {
            existing.status = AgentSessionStatus.Restorable
        }
        existing.cwd = discovered.cwd
        existing.providerSessionId = discovered.providerSessionId
        existing.historyFilePath = discovered.historyFilePath
        existing.summary = discovered.summary
        existing.linkedFiles = discovered.linkedFiles
        existing.createdAt = minOf(existing.createdAt.takeIf { it > 0L } ?: discovered.createdAt, discovered.createdAt)
        existing.updatedAt = maxOf(existing.updatedAt, discovered.updatedAt)
        if (!existing.archived) {
            existing.lastError = null
        }
    }

    private fun resetTransientTerminalState() {
        synchronized(terminalOpenTokensBySessionId) {
            terminalOpenTokensBySessionId.clear()
        }
        stopAllExitMarkerWatchers()
        myState.sessions.forEach { session ->
            if (!session.archived && session.status == AgentSessionStatus.Active) {
                session.status = AgentSessionStatus.Restorable
            }
        }
    }

    private fun markTerminalOpen(sessionId: String, token: String) {
        synchronized(terminalOpenTokensBySessionId) {
            terminalOpenTokensBySessionId.getOrPut(sessionId) { mutableSetOf() }.add(token)
        }
    }

    private fun markTerminalClosed(sessionId: String, token: String) {
        stopExitMarkerWatcher(token)
        TerminalCommandExitMarker.markerFile(token).delete()
        val hasRemainingOpenTerminals = synchronized(terminalOpenTokensBySessionId) {
            val tokens = terminalOpenTokensBySessionId[sessionId] ?: return@synchronized false
            tokens.remove(token)
            if (tokens.isEmpty()) {
                terminalOpenTokensBySessionId.remove(sessionId)
                false
            } else {
                true
            }
        }
        if (!hasRemainingOpenTerminals) {
            repository.find(sessionId)?.let { session ->
                if (!session.archived && session.status == AgentSessionStatus.Active) {
                    session.status = AgentSessionStatus.Restorable
                    repository.update(session)
                }
            }
        }
        notifyTerminalStateChanged()
    }

    private fun clearTerminalOpenState(sessionId: String) {
        val tokens = synchronized(terminalOpenTokensBySessionId) {
            terminalOpenTokensBySessionId.remove(sessionId)
        }.orEmpty()
        tokens.forEach { token ->
            stopExitMarkerWatcher(token)
            TerminalCommandExitMarker.markerFile(token).delete()
        }
    }

    private fun watchExitMarker(sessionId: String, token: String, markerFile: File) {
        val timer = Timer(EXIT_MARKER_POLL_MS) {
            if (markerFile.isFile) {
                markTerminalClosed(sessionId, token)
            }
        }.apply {
            isRepeats = true
        }
        synchronized(exitMarkerTimersByToken) {
            exitMarkerTimersByToken.remove(token)?.stop()
            exitMarkerTimersByToken[token] = timer
        }
        timer.start()
    }

    private fun stopExitMarkerWatcher(token: String) {
        synchronized(exitMarkerTimersByToken) {
            exitMarkerTimersByToken.remove(token)
        }?.stop()
    }

    private fun stopAllExitMarkerWatchers() {
        val timers = synchronized(exitMarkerTimersByToken) {
            exitMarkerTimersByToken.values.toList().also {
                exitMarkerTimersByToken.clear()
            }
        }
        timers.forEach { it.stop() }
    }

    private fun notifyTerminalStateChanged() {
        terminalStateListeners.forEach { listener -> listener() }
    }

    companion object {
        private const val DISCOVERY_THROTTLE_MS = 60_000L
        private const val EXIT_MARKER_POLL_MS = 1_000

        fun getInstance(project: Project): AgentSessionProjectService =
            project.getService(AgentSessionProjectService::class.java)
    }
}
