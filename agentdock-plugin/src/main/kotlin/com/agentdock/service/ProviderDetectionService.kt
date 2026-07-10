package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult
import com.agentdock.terminal.ShellEscaper
import com.agentdock.util.OperatingSystem
import java.io.File
import java.util.concurrent.TimeUnit

class ProviderDetectionService(
    private val os: OperatingSystem = OperatingSystem.current(),
    private val userHome: File = File(System.getProperty("user.home").orEmpty()),
    private val pathEnvironment: String = System.getenv("PATH").orEmpty()
) {
    fun detect(provider: CLIProvider): ProviderDetectionResult {
        if (!provider.enabled) {
            return ProviderDetectionResult.Disabled()
        }

        val executable = provider.executable.trim()
        if (executable.isBlank()) {
            return ProviderDetectionResult.Missing("${provider.displayName} executable is empty")
        }

        val directFile = File(executable)
        if (executable.contains("/") || executable.contains("\\")) {
            return if (directFile.isFile && directFile.canExecute()) {
                ProviderDetectionResult.Available(directFile.absolutePath)
            } else {
                ProviderDetectionResult.Missing("Executable is not available: $executable")
            }
        }

        val pathMatch = findInPath(executable)
            ?: findInCommonDirectories(executable)
            ?: findInVersionManagerDirectories(executable)
            ?: findWithLoginShell(executable)
        return if (pathMatch != null) {
            ProviderDetectionResult.Available(pathMatch.absolutePath)
        } else {
            ProviderDetectionResult.Missing("Executable not found in IDE PATH or login shell: $executable")
        }
    }

    private fun findInPath(executable: String): File? {
        val candidates = if (os == OperatingSystem.Windows) {
            val pathext = System.getenv("PATHEXT")
                ?.split(";")
                ?.filter { it.isNotBlank() }
                ?: listOf(".exe", ".bat", ".cmd")
            listOf(executable) + pathext.map { "$executable$it" }
        } else {
            listOf(executable)
        }

        return pathEnvironment.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> candidates.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun findInCommonDirectories(executable: String): File? {
        val candidates = listOf(
            File(userHome, ".local/bin"),
            File(userHome, "bin"),
            File(userHome, ".bun/bin"),
            File("/opt/homebrew/bin"),
            File("/opt/homebrew/sbin"),
            File("/usr/local/bin"),
            File("/usr/bin"),
            File("/bin")
        )

        return candidates
            .asSequence()
            .map { File(it, executable) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun findInVersionManagerDirectories(executable: String): File? {
        val directDirectories = listOf(
            File(userHome, ".volta/bin"),
            File(userHome, ".asdf/shims"),
            File(userHome, ".local/share/mise/shims"),
            File(userHome, ".npm-global/bin"),
            File(userHome, "Library/pnpm")
        )
        directDirectories
            .asSequence()
            .map { File(it, executable) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.let { return it }

        val versionedDirectories = listOf(
            VersionedExecutableRoot(File(userHome, ".nvm/versions/node"), "bin"),
            VersionedExecutableRoot(File(userHome, ".fnm/node-versions"), "installation/bin"),
            VersionedExecutableRoot(File(userHome, ".local/share/fnm/node-versions"), "installation/bin")
        )
        return versionedDirectories
            .asSequence()
            .flatMap { root ->
                root.directory.listFiles()
                    ?.asSequence()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .map { versionDirectory ->
                        VersionedExecutable(
                            file = File(File(versionDirectory, root.relativeBinDirectory), executable),
                            versionScore = versionScore(versionDirectory.name),
                            modifiedAt = versionDirectory.lastModified()
                        )
                    }
            }
            .filter { it.file.isFile && it.file.canExecute() }
            .maxWithOrNull(compareBy<VersionedExecutable> { it.versionScore }.thenBy { it.modifiedAt })
            ?.file
    }

    private fun versionScore(directoryName: String): Long {
        val components = Regex("\\d+")
            .findAll(directoryName)
            .mapNotNull { it.value.toLongOrNull() }
            .take(3)
            .toList()
        return components.getOrElse(0) { 0L } * 1_000_000_000L +
            components.getOrElse(1) { 0L } * 1_000_000L +
            components.getOrElse(2) { 0L }
    }

    private fun findWithLoginShell(executable: String): File? {
        if (os == OperatingSystem.Windows) return null

        val shell = shellPath() ?: return null
        return listOf("-lc", "-lic")
            .asSequence()
            .mapNotNull { flags -> runShellCommand(shell, flags, "command -v ${ShellEscaper().escape(executable, os)}") }
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .filter { it.startsWith("/") }
            .map { File(it) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun shellPath(): String? {
        val configured = System.getenv("SHELL")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.canExecute() }
        if (configured != null) return configured.absolutePath

        val fallback = if (os == OperatingSystem.Mac) File("/bin/zsh") else File("/bin/sh")
        return fallback.takeIf { it.isFile && it.canExecute() }?.absolutePath
    }

    private fun runShellCommand(shell: String, flags: String, command: String): String? {
        return try {
            val process = ProcessBuilder(shell, flags, command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(3, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private data class VersionedExecutableRoot(
        val directory: File,
        val relativeBinDirectory: String
    )

    private data class VersionedExecutable(
        val file: File,
        val versionScore: Long,
        val modifiedAt: Long
    )
}
