# AgentDock MVP 验收记录

日期: 2026-07-09 03:32 CST  
范围: 根据 `docs/AgentDock-IMPLEMENTATION-PLAN.md` 实施 v0.1 MVP  
实现位置: `agentdock-plugin/`  
插件包: `agentdock-plugin/build/distributions/agentdock-plugin-0.1.1.zip`  

## 1. 本次实施范围

已落地一个 Kotlin/Java 混合的 JetBrains IDE 插件工程:

- Gradle wrapper: `9.6.1`
- IntelliJ Platform Gradle Plugin: `2.17.0`
- Kotlin JVM plugin: `2.2.21`
- 目标 IntelliJ Platform: `ideaIC 2025.2.6.2`
- 插件 id: `com.agentdock`
- 插件入口: 右侧 Tool Window `AgentDock`
- 辅助入口: `Tools > Open AgentDock`, `Tools > AgentDock Settings`
- v0.1 provider: `Codex` 和 `Claude Code`

## 2. 功能实现清单

| 功能 | 状态 | 证据 |
| --- | --- | --- |
| Tool Window 注册 | 通过 | `plugin.xml` 中注册 `toolWindow id="AgentDock"` |
| Tools 菜单入口 | 通过 | `AgentDock.OpenToolWindow`, `AgentDock.OpenSettings` 已加入 `ToolsMenu` |
| Provider 设置 | 通过 | `ProviderSettingsService`, `ProviderSettingsConfigurable` |
| 只内置 Codex / Claude Code | 通过 | `CLIProvider.defaultProviders()` 只返回 `codex`, `claude-code` |
| 会话模型 | 通过 | `AgentSession`, `AgentSessionStatus` |
| 项目级持久化 | 通过 | `AgentSessionProjectService` 使用 `PersistentStateComponent` + `StoragePathMacros.WORKSPACE_FILE` |
| Provider 配置持久化 | 通过 | `ProviderSettingsService` 使用 application-level `PersistentStateComponent` |
| 新建会话 | 通过 | `NewSessionDialog` + `createAndLaunchSession()` |
| 恢复会话 | 通过 | `resumeSession()` + command rendering + JetBrains Terminal launch |
| 搜索/筛选 | 通过 | `SessionFilter` + Tool Window query/provider/status controls |
| 重命名 | 通过 | `RenameSessionDialog` + `renameSession()` |
| 置顶 | 通过 | `togglePin()` + repository sort |
| 归档/恢复归档 | 通过 | `toggleArchive()` + archived filter |
| CLI 缺失状态 | 通过 | `ProviderDetectionService` + `MissingCli` 状态；支持 IDE PATH、常见安装目录和 login shell 检测 |
| Terminal 命令发送 | 通过 | `JetBrainsTerminalLauncher` 创建 Terminal tab 并发送 start/resume command；异常时才复制命令 fallback |
| Notification | 通过 | `AgentDockNotifications` |
| 插件打包 | 通过 | `buildPlugin` 成功, zip 已生成 |
| Plugin Verifier | 通过 | 4 个目标 IDE 全部 `Compatible` |

## 3. 验收命令结果

### 3.1 测试

命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:test
```

结果: 通过。

测试统计:

| 测试类 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| `CLIProviderDefaultsTest` | 2 | 0 | 0 | 0 |
| `ProviderDetectionServiceTest` | 2 | 0 | 0 | 0 |
| `AgentSessionRepositoryTest` | 2 | 0 | 0 | 0 |
| `CommandRendererTest` | 5 | 0 | 0 | 0 |
| `ShellEscaperTest` | 4 | 0 | 0 | 0 |
| `SessionFilterTest` | 3 | 0 | 0 | 0 |

总计: 18 个测试全部通过。

### 3.2 项目配置验证

命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:verifyPluginProjectConfiguration
```

结果: 通过。

说明:

- 初次运行曾提示 Kotlin stdlib 可能与 IntelliJ Platform 自带版本冲突。
- 已在 `gradle.properties` 中加入 `kotlin.stdlib.default.dependency=false` 后重跑通过。

### 3.3 插件结构验证

命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:verifyPluginStructure
```

结果: 通过。

### 3.4 插件打包

命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:buildPlugin
```

结果: 通过。

产物:

```text
agentdock-plugin/build/distributions/agentdock-plugin-0.1.1.zip
```

zip 内容:

```text
agentdock-plugin/lib/agentdock-plugin-0.1.1.jar
agentdock-plugin/lib/agentdock-plugin-0.1.1-searchableOptions.jar
```

### 3.5 Plugin Verifier

命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:verifyPlugin
```

结果: 通过。

Verifier 目标:

| IDE | 结果 |
| --- | --- |
| `IC-252.28539.54` | Compatible |
| `IU-253.33813.25` | Compatible |
| `IU-261.26222.65` | Compatible |
| `IU-262.8665.81` | Compatible |

报告目录:

```text
agentdock-plugin/build/reports/pluginVerifier/IC-252.28539.54/report.html
agentdock-plugin/build/reports/pluginVerifier/IU-253.33813.25/report.html
agentdock-plugin/build/reports/pluginVerifier/IU-261.26222.65/report.html
agentdock-plugin/build/reports/pluginVerifier/IU-262.8665.81/report.html
```

## 4. Patched plugin.xml 核心内容

构建后的 `plugin.xml` 包含:

```xml
<idea-version since-build="252" />
<version>0.1.1</version>
<id>com.agentdock</id>
<name>AgentDock</name>
<depends>com.intellij.modules.platform</depends>
<depends>org.jetbrains.plugins.terminal</depends>
<toolWindow id="AgentDock" anchor="right" icon="/icons/agentDock.svg" factoryClass="com.agentdock.ui.AgentDockToolWindowFactory" />
<applicationService serviceImplementation="com.agentdock.service.ProviderSettingsService" />
<projectService serviceImplementation="com.agentdock.service.AgentSessionProjectService" />
<applicationConfigurable id="agentdock.settings" parentId="tools" displayName="AgentDock" instance="com.agentdock.ui.ProviderSettingsConfigurable" />
<notificationGroup id="AgentDock" displayType="BALLOON" />
<action id="AgentDock.OpenToolWindow" class="com.agentdock.actions.OpenAgentDockToolWindowAction" text="Open AgentDock" />
<action id="AgentDock.OpenSettings" class="com.agentdock.actions.OpenProviderSettingsAction" text="AgentDock Settings" />
```

## 5. 已知限制

1. Terminal 命令发送已接入 JetBrains Terminal。当前实现为:
   - 创建新的 Terminal tab。
   - 将渲染后的 start/resume command 发送到该 tab。
   - 若 Terminal API 调用异常, 才打开 Terminal 并复制命令到剪贴板 fallback。

2. `providerSessionId` 的自动提取未实现。用户可以在创建会话时手动填写, 或后续通过 Settings 修改 resume 模板规避。

3. 尚未实现 v0.2 功能:
   - 文件/文件夹右键启动
   - Terminal tab 绑定
   - 会话详情页
   - Gemini CLI / OpenCode / Junie provider

4. 本次已做本机 PyCharm UI 状态读取验收:
   - PyCharm 2026.2 EAP 日志确认已加载旧版 `AgentDock (0.1.0)`。
   - Computer Use 确认右侧 Tool Window Bar 有 `AgentDock` 按钮。
   - Computer Use 确认 `AgentDock` Tool Window 已展开, 面板可见。
   - `0.1.1` 已安装到 PyCharm 2026.2/2026.1/2025.3 配置目录；当前运行中的 PyCharm 仍需重启后才会加载 `0.1.1`。

5. 已通过自动化验证:
   - Kotlin/Java 编译
   - 单元测试
   - buildSearchableOptions
   - buildPlugin
   - verifyPluginStructure
   - verifyPlugin

## 6. 踩坑记录

### 6.1 本机默认 Java 是 11, 不能构建 IntelliJ 2025.2 插件

现象:

```text
openjdk version "11.0.31"
```

处理:

- Homebrew 已有 OpenJDK 26, 但 IntelliJ 2025.2 Gradle 任务需要 Java 21 toolchain。
- 安装并使用 `openjdk@21`:

```bash
brew install openjdk@21
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ...
```

### 6.2 系统没有 Gradle

现象:

```text
zsh: command not found: gradle
```

处理:

- 下载 Gradle 9.6.1 distribution。
- 用 distribution 生成 Gradle wrapper。
- 最终 wrapper jar 校验:

```text
497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7  gradle/wrapper/gradle-wrapper.jar
```

### 6.3 直接下载 `gradle-9.6.1-wrapper.jar` 不可用

现象:

直接请求 `https://services.gradle.org/distributions/gradle-9.6.1-wrapper.jar` 返回 XML:

```text
NoSuchKey: distributions/gradle-9.6.1-wrapper.jar
```

处理:

- 改用官方 Gradle distribution 运行 `gradle wrapper` 生成 wrapper。

### 6.4 Maven Central 对当前网络返回 403

现象:

Gradle 解析依赖时出现:

```text
Received status code 403 from server: Forbidden
```

处理:

- 在 `settings.gradle.kts` 和子工程 repositories 中增加:

```kotlin
maven("https://maven.aliyun.com/repository/gradle-plugin")
maven("https://maven.aliyun.com/repository/public")
```

### 6.5 `repositoriesMode.FAIL_ON_PROJECT_REPOS` 不适合当前插件工程

现象:

子工程需要声明 `intellijPlatform { defaultRepositories() }`, 但根工程禁止项目仓库:

```text
repository 'maven' was added by build file 'agentdock-plugin/build.gradle.kts'
```

处理:

- 改为:

```kotlin
repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
```

### 6.6 IntelliJ JUnit5 listener 需要 JUnit 4

现象:

```text
Provider com.intellij.tests.JUnit5TestSessionListener could not be instantiated
Caused by: java.lang.NoClassDefFoundError: junit/framework/TestCase
```

处理:

- 增加:

```kotlin
testRuntimeOnly("junit:junit:4.13.2")
```

### 6.7 Plugin Verifier 初次失败: Kotlin ToolWindowFactory 生成内部 API 桥接

现象:

`verifyPlugin` 显示目标 IDE 兼容, 但任务因 `INTERNAL_API_USAGES` 失败。问题集中在 Kotlin 版 `AgentDockToolWindowFactory` 对 `ToolWindowFactory` 默认方法生成的桥接/覆盖。

处理:

- 删除 Kotlin 版 `AgentDockToolWindowFactory.kt`。
- 改为 Java 版 `AgentDockToolWindowFactory.java`, 只实现公开的 `createToolWindowContent()`。
- 重跑 `verifyPlugin` 后 4 个目标 IDE 全部 `Compatible`, 任务通过。

## 7. 验收结论

AgentDock v0.1 MVP 已达到当前落地方案中可自动化验证的验收要求:

- 插件工程可构建。
- Tool Window、Tools 菜单入口、Settings、服务、通知、provider、会话模型、持久化、搜索/筛选、重命名、置顶、归档、Terminal 命令发送均已实现。
- 测试通过。
- 插件打包通过。
- Plugin Verifier 通过 4 个目标 IDE。

下一步建议进入 GUI 手动验收:

1. 重启当前运行中的 PyCharm 2026.2 EAP, 使已安装的 `AgentDock 0.1.1` 生效。
2. 验证右侧 `AgentDock` Tool Window 或 `Tools > Open AgentDock`。
3. 创建 Codex 会话, 确认 Terminal tab 自动打开并执行 Codex start command。
4. 创建 Claude Code 会话, 确认 Terminal tab 自动打开并执行 Claude Code start command。
5. 恢复历史会话, 确认 resume command 会使用检测到的 CLI 绝对路径, 不再因为 IDE PATH 缺少 nvm/Homebrew/local bin 而误判 Missing CLI。
