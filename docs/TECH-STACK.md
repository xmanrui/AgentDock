# AgentDock 技术栈推荐

版本: v0.1  
日期: 2026-07-08  
适用阶段: MVP  
产品形态: JetBrains IDE 插件  
目标 IDE: PyCharm, IntelliJ IDEA, WebStorm, PhpStorm 等 IntelliJ Platform 系列 IDE

## 1. 技术栈结论

MVP 推荐采用:

```text
Kotlin 原生 JetBrains 插件
+ 右侧 Tool Window Bar
+ 原生 Swing / IntelliJ UI Components
+ Kotlin UI DSL
+ PersistentStateComponent
+ Action System
+ TerminalLauncher Adapter
+ Provider 命令模板系统
```

第一版暂时不考虑:

```text
JCEF / React 高级界面
SQLite
云同步
团队版
tmux / zellij 保活 adapter
自研终端
独立桌面端
```

核心产品边界:

> 不负责“保活进程”, 只负责“让项目下的 AI CLI 会话可见、可找、可恢复”。

## 2. 推荐技术栈清单

| 层级 | 推荐技术 | 用途 |
| --- | --- | --- |
| 开发语言 | Kotlin 2.x | 插件主体开发 |
| 构建系统 | Gradle 9.x | 构建、测试、打包、发布 |
| 插件构建 | IntelliJ Platform Gradle Plugin 2.x | JetBrains 插件工程标准工具 |
| 运行环境 | Java Runtime 17 | JetBrains 插件开发基础运行环境 |
| 主界面 | Swing / IntelliJ UI Components | Tool Window 内部 UI |
| 设置页 | Kotlin UI DSL | Provider 路径、命令模板等表单配置 |
| 弹窗 | DialogWrapper | 新建会话、错误详情、确认类弹窗 |
| 入口 | Tool Window API | 右侧 AgentDock 工具窗口 |
| 命令入口 | Action System | 菜单、右键、快捷动作 |
| 本地存储 | PersistentStateComponent | Provider 设置和项目会话 metadata |
| 终端集成 | TerminalLauncher Adapter | 打开 Terminal tab 并执行 CLI 命令 |
| 通知 | NotificationGroup | CLI 缺失、恢复失败等提示 |
| 测试验证 | Plugin Verifier + JUnit 5 | 兼容性和核心逻辑测试 |
| 发布 | JetBrains Marketplace + Gradle publishPlugin | 插件上架和版本发布 |

## 3. 为什么选择 Kotlin

Kotlin 是 JetBrains 插件开发的首选语言。

原因:

1. JetBrains 自家语言, 与 IntelliJ Platform API 配合自然。
2. 相比 Java, Kotlin 写 UI、服务类、数据模型更简洁。
3. 官方插件模板和示例对 Kotlin 支持更好。
4. 面向较新的 IntelliJ Platform 版本, Kotlin 2.x 是更稳妥的长期选择。

建议:

```text
MVP 使用 Kotlin 2.x
不要用 Java 作为主语言
不要用 Groovy/Scala
```

Java 可以作为兼容选择, 但不作为新项目首选。

## 4. 为什么选择 IntelliJ Platform Gradle Plugin 2.x

JetBrains 官方推荐使用 IntelliJ Platform Gradle Plugin 构建插件。2.x 是当前主线, 旧的 Gradle IntelliJ Plugin 1.x 已经不适合作为新项目默认选择。

它负责:

- 拉取目标 IDE 平台依赖
- 运行开发 IDE 实例
- 打包插件
- 执行 Plugin Verifier
- 签名插件
- 发布到 JetBrains Marketplace

推荐命令:

```bash
./gradlew runIde
./gradlew buildPlugin
./gradlew verifyPlugin
./gradlew publishPlugin
```

MVP 不建议自己手写复杂构建逻辑。

## 5. UI 技术选择

### 5.1 Tool Window 主界面

推荐:

```text
Swing / IntelliJ UI Components
```

用于实现:

- AgentDock 右侧面板
- 会话列表
- Provider 筛选
- 搜索框
- 会话卡片
- Open / Rename / Archive / Pin 等操作

理由:

1. 更像 JetBrains IDE 原生功能。
2. 和 IDE 主题、字体、缩放、快捷键兼容性更好。
3. 不需要引入浏览器内核。
4. Marketplace 审核和长期维护压力更小。
5. MVP 交互并不需要复杂 Web UI。

### 5.2 Settings 页面

推荐:

```text
Kotlin UI DSL
```

用于实现:

- Codex executable path
- Claude Code executable path
- Gemini CLI executable path
- start command template
- resume command template
- 默认工作目录策略

理由:

1. Kotlin UI DSL 适合配置表单。
2. 能自动贴合 JetBrains Settings UI 风格。
3. 比手写 Swing 表单更清晰。

### 5.3 弹窗

推荐:

```text
DialogWrapper
```

用于:

- 新建 Agent Session
- CLI 缺失提示
- 恢复失败详情
- 删除/归档确认

理由:

DialogWrapper 会处理 JetBrains IDE 内常见弹窗行为, 包括按钮布局、尺寸记忆、校验提示等。

## 6. 为什么暂时不使用 JCEF / React

MVP 暂时不推荐:

```text
JCEF + React
```

原因:

1. 产品第一版主要是 IDE 工具面板, 不需要复杂 Web UI。
2. JCEF 会增加体积、性能和兼容性复杂度。
3. Web UI 与 IDE 原生主题、快捷键、焦点管理、可访问性集成更麻烦。
4. 插件审核和长期维护成本更高。
5. 当前目标是快速验证“项目级 AI CLI 会话管理”是否有价值。

什么时候再考虑 JCEF:

- 会话详情需要复杂富文本/时间线
- 需要可视化任务图谱
- 需要复杂拖拽看板
- 需要复用现有 Web 前端
- 原生 Swing UI 已明显限制体验

## 7. 数据存储方案

MVP 推荐:

```text
PersistentStateComponent
```

### 7.1 Application-level State

保存全局配置:

- Provider 列表
- CLI executable path
- start command template
- resume command template
- 用户偏好

### 7.2 Project-level State

保存当前项目会话 metadata:

- session id
- session name
- provider
- cwd
- status
- summary
- linked files
- providerSessionId
- archived
- pinned
- updatedAt

### 7.3 为什么不使用 SQLite

MVP 阶段不需要 SQLite。

原因:

1. 会话 metadata 数据量很小。
2. PersistentStateComponent 已能覆盖本地持久化需求。
3. SQLite 会增加 schema migration、锁、备份、损坏恢复等复杂度。
4. 当前不做全文搜索和跨项目大规模聚合。

什么时候再考虑 SQLite:

- 单用户会话数量达到数千级
- 需要全文搜索终端摘要
- 需要复杂查询和统计
- 需要跨项目全局 Dashboard

## 8. Terminal 集成方案

MVP 推荐实现一个隔离层:

```text
TerminalLauncher
ProviderCommandRenderer
CLIProviderRegistry
```

### 8.1 核心流程

```text
用户点击会话
-> 读取 AgentSession metadata
-> 找到 CLIProvider
-> 渲染 resume command
-> 打开或复用 JetBrains Terminal tab
-> 切换到 session.cwd
-> 执行命令
-> 更新 session.updatedAt 和 status
```

### 8.2 Provider 命令模板

示例:

```text
Codex start:
codex

Codex resume:
codex resume {{providerSessionId}}

Claude Code start:
claude

Claude Code resume:
claude --resume {{providerSessionId}}

Gemini CLI start:
gemini

Gemini CLI resume:
gemini chat --resume {{providerSessionId}}
```

注意:

实际命令需要允许用户在 Settings 中修改, 不应全部硬编码。

### 8.3 Terminal API 风险

JetBrains Terminal 相关 API 可能存在版本差异, 所以必须封装 adapter, 避免业务逻辑直接依赖具体 API。

推荐 fallback:

```text
如果无法自动注入命令:
1. 打开 Terminal
2. 复制 resume command 到剪贴板
3. 显示通知让用户粘贴执行
```

这样即使 Terminal API 在某些 IDE 版本不可用, 核心恢复流程仍然可用。

## 9. 为什么不依赖 tmux / zellij

MVP 不依赖 tmux/zellij。

原因:

1. 用户电脑不一定安装。
2. 学习成本高。
3. 和 PyCharm 原生 Terminal / Tool Window 的体验不统一。
4. 本产品的核心价值不是进程保活, 而是会话管理。
5. 引入后会把简单插件变成复杂终端编排工具。

未来可以作为高级 adapter:

```text
tmux adapter
zellij adapter
daemon keepalive adapter
```

但默认路径仍应是软恢复。

## 10. 插件模块设计

推荐目录结构:

```text
agentdock-plugin/
  build.gradle.kts
  settings.gradle.kts
  src/main/kotlin/
    com/agentdock/
      model/
        AgentSession.kt
        CLIProvider.kt
        AgentSessionStatus.kt
      service/
        AgentSessionProjectService.kt
        ProviderSettingsService.kt
      storage/
        AgentSessionRepository.kt
        AgentDockState.kt
        ProviderSettingsState.kt
      terminal/
        TerminalLauncher.kt
        CommandRenderer.kt
        ShellEscaper.kt
      ui/
        AgentDockToolWindowFactory.kt
        AgentDockPanel.kt
        SessionListPanel.kt
        SessionCardPanel.kt
        ProviderSettingsConfigurable.kt
      actions/
        NewAgentSessionAction.kt
        ResumeAgentSessionAction.kt
        ArchiveAgentSessionAction.kt
        AttachFileToSessionAction.kt
      notification/
        AgentDockNotifications.kt
  src/main/resources/
    META-INF/
      plugin.xml
    icons/
      agentDock.svg
      codex.svg
      claude.svg
      gemini.svg
```

## 11. 核心类职责

### 11.1 AgentSessionProjectService

项目级服务。

职责:

- 管理当前项目的 session 列表
- 创建 session
- 更新 session 状态
- 搜索和过滤 session
- 调用 TerminalLauncher 恢复 session

### 11.2 CLIProviderRegistry

Provider 注册中心。

职责:

- 管理 Codex / Claude Code / Gemini CLI provider
- 检测 CLI 是否可用
- 提供 start/resume command template
- 读取用户自定义 provider 设置

### 11.3 TerminalLauncher

终端集成层。

职责:

- 打开 JetBrains Terminal
- 创建或复用 terminal tab
- 切换 cwd
- 执行命令或提供 fallback

### 11.4 ProviderSettingsConfigurable

Settings 页面。

职责:

- 展示 provider 配置
- 保存 executable path
- 保存 command templates
- 校验 provider 配置

### 11.5 AgentDockToolWindowFactory

右侧 Tool Window 入口。

职责:

- 注册 AgentDock 面板
- 初始化 UI
- 绑定 ProjectService 状态

## 12. plugin.xml 依赖建议

MVP 优先声明通用平台能力:

```xml
<depends>com.intellij.modules.platform</depends>
```

如果第一版只做项目、UI、Action、Terminal、PersistentState, 尽量不要声明 Python 专属依赖。

原因:

1. 可以支持更多 JetBrains IDE。
2. 不把产品限制在 PyCharm。
3. 用户需求本质是 AI CLI 会话管理, 不是 Python 语言分析。

只有当后续需要 Python PSI、Python inspections、Python refactor 等能力时, 再考虑:

```xml
<depends optional="true">com.intellij.modules.python</depends>
```

## 13. 兼容性策略

建议第一版目标:

```text
2025.2+
2026.1+
2026.2 EAP 可单独测试
```

原因:

- 太老的 IDE 增加兼容成本。
- Terminal 和 UI API 在新版本更稳定。
- 目标用户大概率是愿意用 AI CLI 和较新 IDE 的重度开发者。

发布时需要通过 Plugin Verifier 检查目标版本。

## 14. 测试策略

### 14.1 单元测试

重点测试:

- command template rendering
- shell escaping
- session search/filter
- provider detection parsing
- storage migration

### 14.2 IDE 集成测试

重点验证:

- Tool Window 是否能打开
- Settings 是否能保存
- 新建 session 是否能写入 Project State
- 点击 session 是否调用 TerminalLauncher
- CLI 缺失时是否显示通知

### 14.3 手动测试矩阵

```text
macOS + PyCharm
macOS + IntelliJ IDEA
macOS + WebStorm
Windows + PyCharm
Linux + PyCharm
```

MVP 可以先从 macOS + PyCharm 开始, 但设计上不要写死 macOS。

## 15. 发布栈

推荐:

```text
GitHub Actions
Gradle buildPlugin
Gradle verifyPlugin
Gradle signPlugin
Gradle publishPlugin
JetBrains Marketplace beta channel
```

第一版发布策略:

1. 本地安装测试
2. Hidden / Beta channel 小范围分发
3. 收集用户反馈
4. 稳定后提交公开 Marketplace

## 16. 不做项和理由

| 暂不考虑 | 理由 |
| --- | --- |
| JCEF / React | MVP UI 不复杂, 原生 UI 更稳 |
| SQLite | metadata 数据量小, PersistentStateComponent 足够 |
| 云同步 | 隐私和账户系统复杂, 不是第一痛点 |
| 团队版 | 先验证单用户高频需求 |
| tmux/zellij 保活 | 学习成本高, 用户电脑不一定安装 |
| 自研终端 | 成本极高, 且 JetBrains 已有 Terminal |
| 独立桌面端 | 会失去 PyCharm 项目上下文优势 |

## 17. MVP 技术路线

### Phase 1: 插件骨架

- 创建 Kotlin 插件工程
- 配置 IntelliJ Platform Gradle Plugin 2.x
- 注册 Tool Window
- 注册 Settings 页面
- 本地 runIde 调试

### Phase 2: 会话模型和存储

- AgentSession model
- CLIProvider model
- Project-level PersistentState
- Application-level Provider Settings
- 会话列表 UI

### Phase 3: 新建和恢复

- NewAgentSessionAction
- Provider 命令模板
- TerminalLauncher adapter
- Codex / Claude Code provider
- CLI 缺失提示

### Phase 4: 会话管理

- 搜索
- 重命名
- 归档
- 置顶
- 从当前文件/文件夹创建会话

### Phase 5: 验证和发布

- Plugin Verifier
- 本地安装测试
- Beta channel 发布
- Marketplace 页面准备

## 18. 最终建议

AgentDock 的第一版不应该追求“终端增强全家桶”, 而应该非常克制:

```text
项目级 AI CLI 会话列表
+ 点击软恢复
+ Provider 命令模板
+ 本地持久化
+ JetBrains 原生体验
```

这条路线的优点:

- 开发周期短
- 技术风险可控
- 用户价值清晰
- 不依赖外部终端工具
- 更容易通过 JetBrains Marketplace
- 后续可以自然扩展到 Pro 功能

## 19. 参考资料

- JetBrains 插件开发文档: https://plugins.jetbrains.com/docs/intellij/developing-plugins.html
- IntelliJ Platform Gradle Plugin 2.x: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- Kotlin for Plugin Developers: https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
- Kotlin UI DSL: https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html
- JetBrains UI Components: https://plugins.jetbrains.com/docs/intellij/user-interface-components.html
- DialogWrapper: https://plugins.jetbrains.com/docs/intellij/dialog-wrapper.html
- Plugin Compatibility: https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
