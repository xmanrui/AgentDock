import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.agentdock"
version = "0.1.28"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.2")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.agentdock"
        name = "AgentDock"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "252"
        }

        description = """
            AgentDock provides project-level Codex and Claude Code CLI session management inside JetBrains IDEs.
        """.trimIndent()

        changeNotes = """
            Adds local Codex and Claude Code session discovery plus a prototype-aligned AgentDock tool window.
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
