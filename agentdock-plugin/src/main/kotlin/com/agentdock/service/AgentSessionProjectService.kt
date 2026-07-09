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
import com.agentdock.terminal.TerminalLaunchResult
import com.agentdock.terminal.TerminalLauncher
import com.agentdock.terminal.TerminalTabPresentation
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

@Service(Service.Level.PROJECT)
@State(name = "AgentDockProjectState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AgentSessionProjectService(private val project: Project) : PersistentStateComponent<AgentDockProjectState> {
    private var myState = AgentDockProjectState()
    private var lastDiscoveryAt = 0L

    private val repository: AgentSessionRepository
        get() = AgentSessionRepository(getState())

    override fun getState(): AgentDockProjectState = StateMigration.migrateProjectState(myState)

    override fun loadState(state: AgentDockProjectState) {
        myState = StateMigration.migrateProjectState(state)
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

        val terminalResult = terminalLauncher.launch(
            command,
            session.cwd,
            TerminalTabPresentation(
                title = SessionTextSanitizer.title(session.name, "Agent session"),
                providerId = provider.id
            )
        )
        return when (terminalResult) {
            is TerminalLaunchResult.Failed -> markFailure(session, terminalResult.message)
            is TerminalLaunchResult.ClipboardFallback -> {
                session.status = AgentSessionStatus.Active
                session.lastError = null
                session.updatedAt = System.currentTimeMillis()
                repository.update(session)
                AgentDockNotifications.info(project, "Command copied", terminalResult.message)
                AgentSessionOperationResult.Success(session, terminalResult)
            }
            is TerminalLaunchResult.Sent -> {
                session.status = AgentSessionStatus.Active
                session.lastError = null
                session.updatedAt = System.currentTimeMillis()
                repository.update(session)
                AgentSessionOperationResult.Success(session, terminalResult)
            }
        }
    }

    private fun markFailure(session: AgentSession, message: String): AgentSessionOperationResult.Failure {
        session.status = AgentSessionStatus.Error
        session.lastError = message
        session.updatedAt = System.currentTimeMillis()
        repository.update(session)
        AgentDockNotifications.error(project, "AgentDock error", message)
        return AgentSessionOperationResult.Failure(session, message)
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
        if (!existing.archived && existing.status != AgentSessionStatus.Active) {
            existing.status = AgentSessionStatus.Restorable
        }
        existing.cwd = discovered.cwd
        existing.providerSessionId = discovered.providerSessionId
        existing.summary = discovered.summary
        existing.linkedFiles = discovered.linkedFiles
        existing.createdAt = minOf(existing.createdAt.takeIf { it > 0L } ?: discovered.createdAt, discovered.createdAt)
        existing.updatedAt = maxOf(existing.updatedAt, discovered.updatedAt)
        if (!existing.archived) {
            existing.lastError = null
        }
    }

    companion object {
        private const val DISCOVERY_THROTTLE_MS = 60_000L

        fun getInstance(project: Project): AgentSessionProjectService =
            project.getService(AgentSessionProjectService::class.java)
    }
}
