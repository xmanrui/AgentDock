package com.agentdock.util

import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ProjectIdentity {
    fun idFor(projectPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(projectPath.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    fun idFor(project: Project): String = idFor(project.basePath ?: project.name)
}
