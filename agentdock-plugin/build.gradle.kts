import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.agentdock"
version = "1.3.1"

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

val generateGifCatalog = tasks.register("generateGifCatalog") {
    val gifDirectory = layout.projectDirectory.dir("src/main/resources/images/gifs")
    val outputDirectory = layout.buildDirectory.dir("generated/resources/gifCatalog")
    inputs.files(fileTree(gifDirectory) { include("*.gif") })
    outputs.dir(outputDirectory)

    doLast {
        val gifNames = fileTree(gifDirectory)
            .matching { include("*.gif") }
            .files
            .map { it.name }
            .sorted()
        val catalog = outputDirectory.get().file("images/gifs/catalog.txt").asFile
        catalog.parentFile.mkdirs()
        catalog.writeText(gifNames.joinToString(separator = "\n", postfix = "\n"))
    }
}

sourceSets {
    main {
        resources.srcDir(generateGifCatalog)
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
            <p>AgentDock brings local Codex CLI, Claude Code CLI, and Gemini CLI sessions into a dedicated tool window where you can:</p>
            <ul>
                <li>discover sessions associated with the current project;</li>
                <li>create, resume, search, rename, pin, and archive sessions;</li>
                <li>launch provider commands in the built-in Terminal tool window;</li>
                <li>view provider usage limits when supported by the local account configuration.</li>
            </ul>
            <p>AgentDock is local-first, does not operate a cloud service, and does not collect analytics or telemetry.</p>
            <p><b>Requirements:</b> JetBrains IDE 2025.2 or later and the locally installed CLI for each provider you use.</p>
            <p>
                <a href="https://github.com/xmanrui/AgentDock">Source code and documentation</a>
                ·
                <a href="https://github.com/xmanrui/AgentDock/blob/main/PRIVACY.md">Privacy Policy</a>
            </p>
            <p>AgentDock is an independent open-source project and is not affiliated with, endorsed by, or sponsored by Google, OpenAI, Anthropic, or JetBrains.</p>
        """.trimIndent()

        changeNotes = """
            <ul>
                <li>Adds animated terminal stream characters with an expanded GIF catalog.</li>
                <li>Improves stream bubble sizing, spacing, task-state transitions, and multi-terminal presentation.</li>
                <li>Handles oversized terminal history records without blocking later lifecycle events.</li>
            </ul>
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
