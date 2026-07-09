package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderDetectionResult
import com.agentdock.terminal.ShellEscaper
import com.agentdock.util.OperatingSystem
import java.io.File
import java.util.concurrent.TimeUnit

class ProviderDetectionService(private val os: OperatingSystem = OperatingSystem.current()) {
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
            ?: findWithLoginShell(executable)
        return if (pathMatch != null) {
            ProviderDetectionResult.Available(pathMatch.absolutePath)
        } else {
            ProviderDetectionResult.Missing("Executable not found in IDE PATH or login shell: $executable")
        }
    }

    private fun findInPath(executable: String): File? {
        val path = System.getenv("PATH").orEmpty()
        val candidates = if (os == OperatingSystem.Windows) {
            val pathext = System.getenv("PATHEXT")
                ?.split(";")
                ?.filter { it.isNotBlank() }
                ?: listOf(".exe", ".bat", ".cmd")
            listOf(executable) + pathext.map { "$executable$it" }
        } else {
            listOf(executable)
        }

        return path.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> candidates.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun findInCommonDirectories(executable: String): File? {
        val home = System.getProperty("user.home").orEmpty()
        val candidates = listOf(
            "$home/.local/bin",
            "$home/bin",
            "$home/.bun/bin",
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin"
        )

        return candidates
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, executable) }
            .firstOrNull { it.isFile && it.canExecute() }
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
}
