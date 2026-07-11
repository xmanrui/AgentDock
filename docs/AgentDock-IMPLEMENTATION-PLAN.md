# AgentDock 落地方案

版本: v0.1  
日期: 2026-07-09  
状态: 实施方案草案  
依据文档: `AgentDock-PRD.md`, `TECH-STACK.md`, `agentdock-prototype.html`  

> 实现状态更新（v1.1）: Gemini CLI 已作为第三个内置 Provider 接入；下文 v0.1 范围和“不做项”保留为历史实施基线。

## 1. 结论

AgentDock 第一版应落地为一个 Kotlin 原生 JetBrains IDE 插件, 以右侧 Tool Window 的形式提供项目级 AI CLI 会话管理。

v0.1 MVP 只支持两个 provider:

- Codex CLI
- Claude Code CLI

第一版不做 Gemini CLI、OpenCode、Junie、云同步、团队协作、进程保活、自研终端和复杂 Web UI。产品核心不是“替代终端”, 而是在 JetBrains IDE 内补齐一层“项目级 AI CLI 会话索引和软恢复能力”。

## 2. 产品形态

### 2.1 插件形态

AgentDock 作为 JetBrains IDE 插件运行, 支持 PyCharm、IntelliJ IDEA、WebStorm、PhpStorm 等 IntelliJ Platform 系列 IDE。

主入口:

- 右侧 Tool Window Bar
- 工具窗口名称: `AgentDock`
- 工具窗口内容: 当前项目下的 Agent Session 列表和操作区

辅助入口:

- Settings 页面: 配置 Codex 和 Claude Code 的可执行路径、启动命令模板、恢复命令模板
- Context Actions: v0.1 可先不做, v0.2 再支持从文件或文件夹右键创建会话
- Status Bar: v0.3 再支持活跃会话数入口

### 2.2 产品原则

1. 项目优先  
   默认只展示当前 JetBrains Project 下的会话, 避免跨项目历史混杂。

2. 会话语义优先  
   会话不是 Terminal tab, 也不是进程。会话是一个可命名、可搜索、可恢复的 AI CLI 工作单元。

3. 软恢复优先  
   v0.1 不保证 CLI 进程保活。点击会话时, 插件重新打开 Terminal 并执行 provider 的 start/resume 命令。

4. 原生体验优先  
   UI 使用 Swing / IntelliJ UI Components / Kotlin UI DSL, 不引入 JCEF / React。

5. 隐私优先  
   默认只保存 metadata, 不保存完整终端输出, 不保存 API key, 不默认同步到云端。

6. 可配置优先  
   CLI 命令模板不硬编码为不可改。Codex 和 Claude Code 的参数变化应通过 Settings 修改模板解决。

## 3. MVP 范围

### 3.1 P0 必做功能

| 编号 | 功能 | 说明 | v0.1 验收标准 |
| --- | --- | --- | --- |
| P0-01 | Tool Window | 注册右侧 `AgentDock` 工具窗口 | PyCharm/IntelliJ 中可打开、关闭、重新打开 |
| P0-02 | 会话列表 | 展示当前项目未归档会话 | 显示名称、provider、状态、cwd、更新时间、摘要 |
| P0-03 | 新建会话 | 支持创建 Codex / Claude Code 会话 | 创建后保存 metadata, 并打开 Terminal 执行 start 命令 |
| P0-04 | 恢复会话 | 点击会话后软恢复 | 打开或复用 Terminal, 切到 cwd, 执行 resume 命令或 fallback |
| P0-05 | 会话命名 | 创建时命名, 后续可重命名 | 名称持久化并用于列表和 Terminal tab 标题 |
| P0-06 | 搜索过滤 | 按名称、摘要、路径、provider 搜索 | 输入后实时过滤, 不需要重启窗口 |
| P0-07 | 归档 | 隐藏不常用会话 | 归档默认隐藏, 可通过 Archived 筛选查看 |
| P0-08 | 置顶 | 关键会话排在前面 | 置顶会话优先于普通会话展示 |
| P0-09 | Provider 设置 | 配置 Codex / Claude Code executable 和命令模板 | 设置保存后新建/恢复使用最新配置 |
| P0-10 | 本地持久化 | 保存 provider 设置和项目会话 metadata | IDE 重启后列表仍存在 |

### 3.2 P1 延后功能

| 编号 | 功能 | 延后原因 |
| --- | --- | --- |
| P1-01 | 文件/文件夹右键启动 | 依赖 Action System 上下文处理, 可在核心流程稳定后补 |
| P1-02 | Terminal tab 绑定 | 需要更稳妥识别 Terminal tab 生命周期 |
| P1-03 | 会话详情页 | v0.1 列表卡片先承载核心信息 |
| P1-04 | Provider 自动检测增强 | v0.1 做基础检测, 更细的版本识别后置 |
| P1-05 | 最近活动摘要 | 不保存终端输出前提下摘要来源有限 |
| P1-06 | Status Bar 入口 | 不是核心恢复路径 |
| P1-07 | 导入现有会话 | 依赖 provider session id 规则验证 |

### 3.3 明确不做

- 不支持 Gemini CLI、OpenCode、Junie
- 不做云同步和团队空间
- 不做 tmux / zellij 默认依赖
- 不做自研 shell / PTY
- 不保存完整终端日志
- 不内置或代理任何大模型服务
- 不管理用户 AI API key

## 4. 核心用户流程

### 4.1 首次使用

1. 用户安装 AgentDock 插件。
2. 打开 PyCharm 项目。
3. 右侧出现 `AgentDock` Tool Window。
4. 面板展示空状态:
   - `New Codex Session`
   - `New Claude Code Session`
   - `Configure Providers`
5. 用户进入 Settings 配置或确认 `codex` / `claude` 可执行路径。

验收点:

- 未配置 provider 时, UI 不崩溃。
- CLI 缺失时显示 `Missing CLI`, 并提供打开 Settings 的操作。

### 4.2 新建 Codex 会话

1. 用户点击 `New Session`。
2. 选择 `Codex`。
3. 输入会话名、工作目录、摘要。
4. 插件创建 `AgentSession` metadata。
5. 插件打开 Terminal, 切换到 session cwd。
6. 插件执行 Codex start command。
7. 会话状态变为 `Active`。

验收点:

- 会话出现在列表顶部。
- metadata 写入项目级状态。
- Terminal tab 标题尽量显示 `Codex · {Session Name}`。
- 如果无法自动执行命令, 至少打开 Terminal、复制命令并提示用户粘贴执行。

### 4.3 恢复 Claude Code 会话

1. 用户点击历史 Claude Code 会话。
2. 插件读取会话 provider、cwd、providerSessionId、resumeCommand。
3. 插件检测 `claude` 是否可用。
4. 插件渲染 resume command。
5. 插件打开 Terminal 并执行命令。
6. 更新 `updatedAt` 和状态。

验收点:

- 有 `providerSessionId` 时, resume command 包含对应 id。
- 没有 `providerSessionId` 时, UI 应提示恢复信息不足, 不应静默失败。
- 恢复失败时状态变为 `Error`, 卡片显示错误摘要。

### 4.4 管理会话

支持:

- Rename
- Pin
- Archive / Unarchive
- Search
- Filter by provider
- Filter by status

验收点:

- 操作都能持久化。
- 搜索和筛选组合后结果稳定。
- 归档会话默认隐藏, 但不会被删除。

## 5. 数据模型

### 5.1 AgentSession

```kotlin
data class AgentSession(
    var id: String = "",
    var projectId: String = "",
    var projectPath: String = "",
    var name: String = "",
    var providerId: String = "",
    var status: AgentSessionStatus = AgentSessionStatus.Restorable,
    var cwd: String = "",
    var providerSessionId: String? = null,
    var terminalTabId: String? = null,
    var summary: String = "",
    var linkedFiles: MutableList<String> = mutableListOf(),
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var lastError: String? = null
)
```

### 5.2 AgentSessionStatus

```kotlin
enum class AgentSessionStatus {
    Active,
    Restorable,
    MissingCli,
    Error,
    Archived
}
```

状态含义:

| 状态 | 含义 | 触发条件 |
| --- | --- | --- |
| Active | 当前 IDE 中刚启动或已打开对应 Terminal | 新建成功或恢复命令已发送 |
| Restorable | 有 metadata 和恢复信息, 进程不一定存活 | IDE 重启后默认状态 |
| MissingCli | provider executable 不可用 | 检测失败或路径不存在 |
| Error | 最近一次创建或恢复失败 | Terminal 启动、cwd、命令渲染等失败 |
| Archived | 已归档 | 用户归档 |

### 5.3 CLIProvider

```kotlin
data class CLIProvider(
    var id: String = "",
    var displayName: String = "",
    var executable: String = "",
    var detectCommand: String = "",
    var startCommandTemplate: String = "",
    var resumeCommandTemplate: String = "",
    var supportsSessionId: Boolean = true,
    var supportsImport: Boolean = false,
    var enabled: Boolean = true
)
```

v0.1 默认 provider:

| id | displayName | executable | startCommandTemplate | resumeCommandTemplate |
| --- | --- | --- | --- | --- |
| `codex` | `Codex` | `codex` | `codex` | `codex resume {{providerSessionId}}` |
| `claude-code` | `Claude Code` | `claude` | `claude` | `claude --resume {{providerSessionId}}` |

说明:

- 具体命令要在开发前用当前 CLI 版本验证。
- 模板必须允许用户在 Settings 中修改。
- `providerSessionId` 允许为空, 但执行精确恢复前必须校验。

### 5.4 ProviderCommandContext

```kotlin
data class ProviderCommandContext(
    val provider: CLIProvider,
    val session: AgentSession,
    val projectPath: String,
    val shell: String,
    val os: OperatingSystem
)
```

用于渲染命令模板, 支持变量:

| 变量 | 说明 |
| --- | --- |
| `{{providerSessionId}}` | provider 原生 session id |
| `{{sessionName}}` | AgentDock 会话名 |
| `{{cwd}}` | 工作目录 |
| `{{projectPath}}` | 项目路径 |

## 6. 存储方案

### 6.1 Application-level State

保存跨项目配置:

- Codex executable path
- Claude Code executable path
- start command template
- resume command template
- provider enabled
- 用户偏好, 例如默认 provider、排序方式

建议类:

- `ProviderSettingsService`
- `ProviderSettingsState`

### 6.2 Project-level State

保存当前项目的会话索引:

- sessions
- archived sessions
- pinned sessions
- last selected filter
- schemaVersion

建议类:

- `AgentSessionProjectService`
- `AgentSessionRepository`
- `AgentDockProjectState`

### 6.3 存储位置原则

优先使用 `PersistentStateComponent`。

默认不把会话索引写入 Git 追踪文件。若使用项目 `.idea` 存储, 需要确认写入位置不会误提交个人会话信息。更稳妥的第一版策略是使用 JetBrains 的 workspace / project-level private state。

### 6.4 Migration

状态对象必须包含 `schemaVersion`:

```kotlin
data class AgentDockProjectState(
    var schemaVersion: Int = 1,
    var sessions: MutableList<AgentSession> = mutableListOf()
)
```

v0.1 只需要支持初始 schema。后续增加字段时, 通过 repository 层集中迁移。

## 7. 技术栈

| 层级 | 选择 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin 2.x | JetBrains 插件开发首选 |
| Runtime | Java 17 | IntelliJ Platform 插件基础运行环境 |
| 构建 | Gradle 9.x | 构建、测试、打包 |
| 插件构建 | IntelliJ Platform Gradle Plugin 2.x | 官方插件工程工具链 |
| UI | Swing / IntelliJ UI Components | Tool Window 原生 UI |
| Settings | Kotlin UI DSL | Provider 配置表单 |
| Dialog | DialogWrapper | 新建、重命名、错误详情 |
| 存储 | PersistentStateComponent | 本地 metadata 和 provider 设置 |
| Action | Action System | Toolbar、菜单、后续右键入口 |
| Terminal | TerminalLauncher Adapter | 隔离 JetBrains Terminal API 风险 |
| 通知 | NotificationGroup | CLI 缺失、恢复失败、fallback 提示 |
| 测试 | JUnit 5, IntelliJ test framework | 核心逻辑和插件集成测试 |
| 兼容验证 | Plugin Verifier | 多 IDE 版本兼容检查 |

### 7.1 不采用的技术

| 技术 | 不采用原因 |
| --- | --- |
| JCEF / React | v0.1 UI 不复杂, 原生 UI 更稳 |
| SQLite | 会话 metadata 数据量小, PersistentStateComponent 足够 |
| tmux / zellij | 不应成为普通用户前置依赖 |
| 自研终端 | 成本高且偏离产品核心 |
| 云同步 | 涉及账户、隐私、冲突合并, 不属于 v0.1 |

## 8. 代码结构

建议插件工程目录:

```text
agentdock-plugin/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  src/main/kotlin/com/agentdock/
    model/
      AgentSession.kt
      AgentSessionStatus.kt
      CLIProvider.kt
      ProviderCommandContext.kt
      ProviderDetectionResult.kt
    service/
      AgentSessionProjectService.kt
      CLIProviderRegistry.kt
      ProviderSettingsService.kt
      ProviderDetectionService.kt
    storage/
      AgentDockProjectState.kt
      ProviderSettingsState.kt
      AgentSessionRepository.kt
      StateMigration.kt
    terminal/
      TerminalLauncher.kt
      JetBrainsTerminalLauncher.kt
      TerminalLaunchResult.kt
      CommandRenderer.kt
      ShellEscaper.kt
      ClipboardCommandFallback.kt
    ui/
      AgentDockToolWindowFactory.kt
      AgentDockPanel.kt
      SessionListPanel.kt
      SessionCardPanel.kt
      SessionToolbar.kt
      NewSessionDialog.kt
      RenameSessionDialog.kt
      ProviderSettingsConfigurable.kt
    actions/
      NewAgentSessionAction.kt
      ResumeAgentSessionAction.kt
      RenameAgentSessionAction.kt
      PinAgentSessionAction.kt
      ArchiveAgentSessionAction.kt
      OpenProviderSettingsAction.kt
    notification/
      AgentDockNotifications.kt
    util/
      TimeFormatter.kt
      ProjectIdentity.kt
      IdGenerator.kt
  src/main/resources/
    META-INF/
      plugin.xml
    icons/
      agentDock.svg
      codex.svg
      claude.svg
  src/test/kotlin/com/agentdock/
    terminal/
      CommandRendererTest.kt
      ShellEscaperTest.kt
    service/
      AgentSessionProjectServiceTest.kt
      CLIProviderRegistryTest.kt
    storage/
      AgentSessionRepositoryTest.kt
    ui/
      SessionFilterTest.kt
```

### 8.1 模块职责

| 模块 | 职责 |
| --- | --- |
| `model` | 纯数据模型和枚举, 不依赖 IDE UI |
| `service` | 会话创建、更新、恢复编排和 provider 管理 |
| `storage` | PersistentStateComponent 读写和 migration |
| `terminal` | Terminal 打开、命令渲染、shell escaping、fallback |
| `ui` | Tool Window、列表、卡片、弹窗、Settings |
| `actions` | JetBrains Action System 接入 |
| `notification` | 统一错误和提示 |
| `util` | id、时间、项目身份等通用工具 |

### 8.2 关键接口

`TerminalLauncher`:

```kotlin
interface TerminalLauncher {
    fun launch(command: String, cwd: String, tabTitle: String): TerminalLaunchResult
}
```

`CommandRenderer`:

```kotlin
interface CommandRenderer {
    fun render(template: String, context: ProviderCommandContext): String
}
```

`CLIProviderRegistry`:

```kotlin
interface CLIProviderRegistry {
    fun listEnabledProviders(): List<CLIProvider>
    fun getProvider(providerId: String): CLIProvider?
    fun detect(providerId: String): ProviderDetectionResult
}
```

## 9. 运行流程设计

### 9.1 新建会话流程

```text
NewSessionDialog submit
-> AgentSessionProjectService.createSession()
-> CLIProviderRegistry.getProvider()
-> ProviderDetectionService.detect()
-> AgentSessionRepository.save()
-> CommandRenderer.render(startCommandTemplate)
-> TerminalLauncher.launch()
-> update status Active / MissingCli / Error
-> AgentDockPanel refresh
```

失败策略:

- provider 不存在: 阻止创建, 显示错误。
- CLI 缺失: 可创建 metadata, 状态为 `MissingCli`, 引导 Settings。
- Terminal 自动执行失败: 保存 metadata, 复制命令到剪贴板, 显示 fallback 通知。

### 9.2 恢复会话流程

```text
SessionCard Open
-> AgentSessionProjectService.resumeSession(sessionId)
-> validate session exists
-> validate cwd exists
-> validate provider executable
-> validate providerSessionId if template requires it
-> CommandRenderer.render(resumeCommandTemplate)
-> TerminalLauncher.launch()
-> update updatedAt and status
-> refresh list
```

失败策略:

- cwd 不存在: 状态 `Error`, 提供选择新 cwd 的后续入口。
- providerSessionId 缺失: 状态保持 `Restorable`, 弹出提示要求补充 session id 或修改 resume 模板。
- CLI 缺失: 状态 `MissingCli`, 打开 Settings。
- Terminal API 不可用: fallback 到复制命令。

### 9.3 搜索排序流程

排序规则:

1. pinned
2. status priority: Active > Restorable > MissingCli > Error > Archived
3. updatedAt desc
4. createdAt desc

搜索字段:

- name
- summary
- provider displayName
- cwd
- linkedFiles

## 10. UI 方案

### 10.1 Tool Window 布局

```text
AgentDock
├─ Header
│  ├─ title: AgentDock
│  ├─ active/restorable count
│  ├─ New Session
│  └─ Settings
├─ Search
├─ Filter chips
│  ├─ All
│  ├─ Codex
│  ├─ Claude Code
│  ├─ Active
│  ├─ Restorable
│  ├─ Missing CLI
│  └─ Archived
├─ Session list
│  └─ Session card
└─ Provider health footer
```

### 10.2 Session Card

展示字段:

- Provider icon
- Session name
- Status
- cwd
- updatedAt
- summary
- linked file chips
- actions: Open, Rename, Pin, Archive

设计要求:

- 信息密度贴近 IDE 工具面板, 不做营销页式大卡片。
- 状态颜色克制, 与 JetBrains 主题兼容。
- 卡片宽度适应右侧 Tool Window。
- 操作按钮可用图标或短文本, hover 提示含义。

### 10.3 Settings 页面

路径建议:

```text
Settings / Tools / AgentDock
```

字段:

| 字段 | Codex | Claude Code |
| --- | --- | --- |
| Enabled | checkbox | checkbox |
| Executable | text field + browse | text field + browse |
| Detect command | text field | text field |
| Start command template | text field | text field |
| Resume command template | text field | text field |
| Test provider | button | button |

Settings 验收:

- 修改后可 Apply / OK 保存。
- Test provider 能显示成功、失败和版本输出摘要。
- 不保存任何 API key。

## 11. Terminal 集成策略

### 11.1 Adapter 优先

业务层只依赖 `TerminalLauncher` 接口, 不直接调用 JetBrains Terminal API。

实现优先级:

1. 使用稳定可用的 JetBrains Terminal API 打开 tab 并执行命令。
2. 如果 API 在目标版本不稳定, 使用 Action System 打开 Terminal, 再尝试发送命令。
3. 如果自动发送不可用, 打开 Terminal、复制命令到剪贴板、显示通知。

### 11.2 命令安全

命令渲染必须经过:

- 变量存在性校验
- shell escaping
- cwd 存在性校验
- provider executable 校验

v0.1 不执行用户不可见的后台命令恢复会话, 所有 CLI 交互都在 IDE Terminal 中发生。

## 12. 测试方案

### 12.1 单元测试

| 用例编号 | 测试对象 | 场景 | 期望 |
| --- | --- | --- | --- |
| UT-01 | CommandRenderer | 渲染 `codex resume {{providerSessionId}}` | 正确替换 session id |
| UT-02 | CommandRenderer | 缺少 providerSessionId | 返回明确错误, 不生成半截命令 |
| UT-03 | ShellEscaper | cwd 含空格 | shell 参数正确转义 |
| UT-04 | ShellEscaper | session name 含引号 | 不破坏命令结构 |
| UT-05 | CLIProviderRegistry | 默认 provider 初始化 | 只包含 Codex 和 Claude Code |
| UT-06 | ProviderSettingsService | 修改 executable | 保存并重新读取一致 |
| UT-07 | AgentSessionProjectService | 创建会话 | 生成 id、createdAt、updatedAt、默认状态 |
| UT-08 | AgentSessionProjectService | rename | 名称更新并持久化 |
| UT-09 | AgentSessionProjectService | pin | pinned 会话排序靠前 |
| UT-10 | AgentSessionProjectService | archive | 默认列表隐藏 archived |
| UT-11 | SessionFilter | 按 provider 搜索 | 只返回匹配 provider |
| UT-12 | SessionFilter | 按中文摘要搜索 | 可匹配中文文本 |
| UT-13 | AgentSessionRepository | schemaVersion 缺失 | 使用默认 migration |
| UT-14 | ProjectIdentity | 同路径项目 | projectId 稳定 |

### 12.2 IDE 集成测试

| 用例编号 | 场景 | 步骤 | 期望 |
| --- | --- | --- | --- |
| IT-01 | Tool Window 注册 | 启动测试 IDE | 右侧存在 AgentDock |
| IT-02 | Tool Window 打开 | 点击 AgentDock | 面板正常渲染空状态 |
| IT-03 | Settings 保存 | 修改 Codex executable 后 Apply | 重新打开仍存在 |
| IT-04 | 新建 Codex 会话 | 提交 New Session | Project State 出现会话 |
| IT-05 | 新建 Claude Code 会话 | 提交 New Session | providerId 为 `claude-code` |
| IT-06 | CLI 缺失 | 设置不存在的 executable | 卡片状态为 MissingCli |
| IT-07 | 恢复会话 | 点击 Open | TerminalLauncher 被调用 |
| IT-08 | Terminal fallback | 模拟 Terminal API 不可用 | 命令复制到剪贴板并通知 |
| IT-09 | 重启持久化 | 创建会话后重启测试 IDE | 会话仍展示 |
| IT-10 | 归档视图 | archive 后切换 Archived filter | 会话可见 |

### 12.3 手动测试矩阵

MVP 最小矩阵:

| OS | IDE | 必测内容 |
| --- | --- | --- |
| macOS | PyCharm | 完整新建、恢复、Settings、持久化 |
| macOS | IntelliJ IDEA | Tool Window、Settings、Terminal fallback |
| macOS | WebStorm | 无 Python 依赖验证 |

发布前扩展矩阵:

| OS | IDE | 必测内容 |
| --- | --- | --- |
| Windows | PyCharm | shell escaping、cwd、命令模板 |
| Linux | PyCharm | executable 检测、Terminal 打开 |

### 12.4 CLI 兼容性验证

开发前需要手动确认:

- `codex --version` 是否可用于检测
- `claude --version` 或等价命令是否可用于检测
- Codex 当前 resume 命令格式
- Claude Code 当前 resume 命令格式
- 两者是否能稳定暴露 machine-readable session id
- 无 session id 时是否有可接受 fallback

验证结果应记录到后续开发文档或 ADR。

## 13. 验收标准

### 13.1 功能验收

v0.1 完成必须满足:

1. 安装插件后, 目标 IDE 右侧出现 `AgentDock` Tool Window。
2. 空项目打开时, 面板不报错, 显示空状态。
3. 可以配置 Codex 和 Claude Code provider。
4. 可以创建 Codex 会话并保存到本地。
5. 可以创建 Claude Code 会话并保存到本地。
6. 可以点击会话执行软恢复流程。
7. CLI 缺失时显示 `Missing CLI`, 不崩溃。
8. 恢复失败时显示错误提示, 不静默失败。
9. 会话可重命名、置顶、归档。
10. 搜索可以按名称、摘要、provider、cwd 过滤。
11. IDE 重启后, 会话列表仍存在。
12. 不保存 API key 和完整终端输出。

### 13.2 技术验收

1. `./gradlew test` 通过。
2. `./gradlew buildPlugin` 通过。
3. `./gradlew verifyPlugin` 通过目标 IDE 版本。
4. 核心逻辑单元测试覆盖:
   - command rendering
   - shell escaping
   - session filtering
   - provider settings
   - repository persistence
5. 插件不声明 Python 专属依赖。
6. Terminal 集成通过 adapter 隔离, UI/service 不直接散落 Terminal API 调用。
7. Provider 命令模板不硬编码在 UI 层。

### 13.3 产品验收

1. 新用户能在 2 分钟内完成 provider 配置和首次会话创建。
2. 已有会话点击恢复路径不超过 1 次点击。
3. 平均找回项目内会话时间小于 5 秒。
4. AgentDock 面板在 300-420px 宽度下信息不溢出。
5. 深色和浅色 IDE 主题下均可读。
6. 所有错误状态都有下一步操作。

## 14. 开发里程碑

### Milestone 0: 工程初始化

产出:

- Kotlin 插件工程
- Gradle 配置
- plugin.xml
- runIde 可启动

验收:

- `./gradlew runIde` 可打开目标 IDE。
- 插件可以被 IDE 识别。

### Milestone 1: Tool Window 和静态 UI

产出:

- `AgentDockToolWindowFactory`
- `AgentDockPanel`
- 空状态
- 静态 session card 样式

验收:

- 右侧 Tool Window 可打开。
- 面板布局贴近原型。

### Milestone 2: 模型和存储

产出:

- `AgentSession`
- `CLIProvider`
- Project-level state
- Application-level provider settings
- Repository

验收:

- 创建测试 session 后可持久化。
- IDE 重启后数据仍在。

### Milestone 3: Provider 设置

产出:

- Settings 页面
- Codex provider
- Claude Code provider
- provider detection 基础能力

验收:

- 修改 executable 和模板后保存生效。
- CLI 缺失可识别并提示。

### Milestone 4: 新建和恢复流程

产出:

- New Session dialog
- `TerminalLauncher`
- `CommandRenderer`
- fallback 到剪贴板

验收:

- Codex 和 Claude Code 都能创建会话。
- 点击 Open 会走恢复流程。
- Terminal API 失败时 fallback 可用。

### Milestone 5: 会话管理

产出:

- Rename
- Pin
- Archive / Unarchive
- Search / filter
- 状态刷新

验收:

- 所有操作持久化。
- 排序和筛选符合规则。

### Milestone 6: 验证和打包

产出:

- 单元测试
- IDE 集成测试
- Plugin Verifier
- buildPlugin 包
- 手动测试记录

验收:

- 达到 13 章验收标准。
- 可本地安装插件 zip。

## 15. 风险和应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| JetBrains Terminal API 不稳定 | 无法自动打开或执行命令 | 使用 TerminalLauncher adapter 和剪贴板 fallback |
| Codex/Claude Code session id 不稳定 | 无法精确 resume | providerSessionId 可空, UI 提示补充, 命令模板可配置 |
| 用户误解为进程保活 | 预期落差 | UI 明确区分 Active 和 Restorable |
| CLI 参数更新 | 恢复失败 | Settings 可修改 start/resume 模板 |
| `.idea` 泄露个人会话 | 隐私风险 | 默认使用私有 project/application state, 不保存完整终端输出 |
| Windows shell 差异 | 命令失败 | ShellEscaper 按 OS 测试 |
| 插件审核失败 | 发布延迟 | 避免私有 API, 使用 Plugin Verifier |

## 16. 后续演进

### v0.2

- Gemini CLI / OpenCode / Junie provider
- 文件和文件夹右键启动会话
- Terminal tab 绑定
- 会话详情页
- provider 自动检测增强

### v0.3

- 导入现有 CLI 会话
- 高级搜索
- 最近活动摘要
- Status Bar 入口
- 错误诊断面板

### v1.0

- Marketplace 上架
- 多 JetBrains IDE 家族兼容
- 远程开发基础支持
- Pro 功能验证

## 17. 第一版交付清单

代码交付:

- Kotlin 插件工程
- AgentDock Tool Window
- Codex / Claude Code provider
- Provider Settings
- Session metadata persistence
- New / Open / Rename / Pin / Archive
- TerminalLauncher adapter
- Clipboard fallback
- NotificationGroup
- 单元测试和集成测试

文档交付:

- README
- 安装和本地调试说明
- Provider 命令模板说明
- 手动测试记录
- 已知限制

发布交付:

- `buildPlugin` zip
- Plugin Verifier 报告
- Marketplace beta channel 描述草稿

## 18. 推荐立即执行的下一步

1. 创建 `agentdock-plugin/` Kotlin 插件工程。
2. 用 `runIde` 验证 Tool Window skeleton。
3. 实现 `AgentSession` / `CLIProvider` / `PersistentStateComponent`。
4. 实现 Codex 和 Claude Code provider settings。
5. 在 macOS + PyCharm 下打通 New Session 到 Terminal fallback 的最短闭环。
