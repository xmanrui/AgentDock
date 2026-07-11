import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.agentdock"
version = "0.1.84"

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

    named("verifyPluginSignature") {
        dependsOn("signPlugin")
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
            <p>Manage project-scoped AI CLI sessions without leaving your JetBrains IDE.</p>
            <p>AgentDock brings local Codex CLI and Claude Code CLI sessions into a dedicated tool window where you can:</p>
            <ul>
                <li>discover sessions associated with the current project;</li>
                <li>create, resume, search, rename, pin, and archive sessions;</li>
                <li>launch provider commands in the built-in Terminal tool window;</li>
                <li>view provider usage limits when supported by the local account configuration.</li>
            </ul>
            <p>AgentDock is local-first, does not operate a cloud service, and does not collect analytics or telemetry.</p>
            <p><b>Requirements:</b> JetBrains IDE 2025.2 or later and a locally installed Codex CLI or Claude Code CLI for the corresponding provider.</p>
            <p>
                <a href="https://github.com/xmanrui/AgentDock">Source code and documentation</a>
                ·
                <a href="https://github.com/xmanrui/AgentDock/blob/main/PRIVACY.md">Privacy Policy</a>
            </p>
            <p>AgentDock is an independent open-source project and is not affiliated with, endorsed by, or sponsored by OpenAI, Anthropic, or JetBrains.</p>
        """.trimIndent()

        changeNotes = """
            Matches usage-header badges to the light mint pill reference style.
        """.trimIndent()
    }

    signing {
        providers.environmentVariable("CERTIFICATE_CHAIN_FILE").orNull?.let {
            certificateChainFile = file(it)
        }
        providers.environmentVariable("PRIVATE_KEY_FILE").orNull?.let {
            privateKeyFile = file(it)
        }
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
