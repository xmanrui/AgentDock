# AgentDock for JetBrains IDEs 产品需求文档

版本: v0.1  
日期: 2026-07-09  
状态: 产品原型阶段  
目标平台: PyCharm, IntelliJ IDEA, WebStorm, PhpStorm 等 JetBrains IDE 家族  
产品形态建议: JetBrains IDE 插件, 右侧 Tool Window Bar 入口

## 1. 背景

开发者正在同时使用多个 AI CLI 工具完成真实代码任务, 例如 Codex CLI, Claude Code CLI, Gemini CLI, OpenCode, Junie 等。它们各自有自己的会话、恢复命令和历史记录, 但在 IDE 内部通常只是表现为一个普通终端标签页。

AgentDock 的当前阶段先聚焦 Codex CLI 和 Claude Code CLI 的会话管理。Gemini CLI、OpenCode、Junie 等 provider 保留为后续扩展方向, 不进入 v0.1 MVP 范围。

PyCharm 这类 JetBrains IDE 已经是用户查看代码、编辑文档、运行测试、打开终端的主工作台。用户真正想要的不是再打开一个独立终端应用, 而是在同一个项目工作区里看到:

- 当前项目有哪些 AI 会话
- 每个会话在做什么任务
- 使用的是哪一个 AI CLI
- 点击会话后能回到对应的 CLI 对话
- 重启 IDE 后仍然能看到这些会话入口

JetBrains 终端本身已经支持保存终端标签名、工作目录和 shell history, 也支持从终端入口启动部分 AI agent, 但它没有提供“项目维度的多 AI CLI 会话管理”。用户仍然需要在 terminal tabs、CLI resume 列表、shell history 和项目目录之间来回找。

## 2. 问题定义

当前用户痛点集中在“AI CLI 会话不可见”和“恢复体验差”:

1. 一个项目下会同时有多个 AI 任务, 例如修 bug、写文档、做重构、跑数据、分析方案, 普通终端 tab 很难表达任务语义。
2. Codex CLI 或 Claude Code CLI 自带的 resume 能恢复, 但历史列表往往跨项目混杂, 标题和上下文不够清晰, 翻找成本高。
3. IDE 重启后, 终端进程不一定仍然存活。即使终端 tab 恢复, 也不等于 AI CLI 会话语义恢复。
4. Warp, Zellij, tmux 等工具可以管理终端或进程, 但它们不理解 JetBrains 项目、文件树、编辑器上下文和用户的代码任务。
5. 用户不一定愿意安装或学习 zellij/tmux, 也不希望为了会话管理牺牲 IDE 性能。

核心需求可以概括为:

> 在 PyCharm 里像管理 Codex Projects 一样管理“某个项目下的多个 AI CLI 会话”, 点击会话即可在 IDE 终端中恢复或继续对应 CLI。

## 3. 产品定位

AgentDock 是一个 JetBrains IDE 插件, 面向重度 AI CLI 开发者, 提供项目级 AI CLI 会话管理。

它不是一个新的 AI 模型服务, 也不是完整终端替代品。它更像是 IDE 内部的“AI CLI 会话层”:

- 上层: 项目, 会话, 任务名, AI provider, 状态, 最近活动, 关联文件
- 中层: 会话索引, 搜索, 归档, 置顶, 命令模板
- 下层: 调用 JetBrains Terminal 打开 tab, 切换工作目录, 执行对应 CLI 的 start/resume 命令

## 4. 目标用户

### 4.1 核心用户

- 使用 PyCharm/IntelliJ/WebStorm 等 JetBrains IDE 的开发者
- 同时使用 Codex CLI 和 Claude Code CLI, 并可能后续接入更多 AI CLI
- 一个项目内会并行推进多个 AI 辅助任务
- 对 IDE 集成、项目上下文、文件跳转和终端体验有较高依赖

### 4.2 扩展用户

- AI native 团队中的技术负责人或产品工程师
- 需要审计 AI 任务历史的开发团队
- 经常在多个代码仓库之间切换的独立开发者
- 需要把“需求、文档、代码、AI 对话”放在同一工作区处理的用户

## 5. 产品目标

### 5.1 MVP 目标

- 在右侧 Tool Window Bar 提供 AgentDock 入口
- 展示当前项目下的多个 AI CLI 会话
- 支持新建和恢复 Codex、Claude Code 两类 CLI 会话
- 支持点击会话后在 IDE Terminal 中恢复或继续
- 支持搜索、重命名、归档、置顶和状态展示
- 支持配置 Codex、Claude Code 的可执行路径和 resume 命令模板
- 不强依赖 zellij/tmux
- 不强制保活 CLI 进程

### 5.2 MVP Provider 范围

v0.1 MVP 只内置两个 provider:

- Codex CLI
- Claude Code CLI

Gemini CLI、OpenCode、Junie 和其他外部 agent 不作为 v0.1 交付目标。MVP 的 provider 配置、命令模板和数据模型应保留扩展能力, 但 UI、验收标准和测试矩阵只以 Codex 和 Claude Code 为准。

### 5.3 非目标

MVP 阶段不做以下内容:

- 不替代 JetBrains Terminal
- 不自己实现完整 shell/PTY 终端
- 不托管用户的 AI API key
- 不提供自己的大模型服务
- 不默认把会话同步到云端
- 不承诺关闭 IDE 后保持 CLI 进程继续运行
- 不要求用户安装 zellij/tmux
- 不深度修改 JetBrains 内置 Terminal UI
- 不内置 Gemini CLI、OpenCode、Junie 等非 Codex/Claude Code provider

## 6. 产品形态

推荐形态: JetBrains IDE 插件。

### 6.1 入口位置

主入口放在 IDE 右侧 Tool Window Bar, 名称为 `AgentDock`。

理由:

- 符合 JetBrains 插件生态的常见交互模式
- 不打断编辑器和终端的主工作流
- 用户可以随时折叠, 不占用主编辑区域
- 右侧面板天然适合展示会话列表、详情和快捷操作

### 6.2 主要 UI 区域

1. Tool Window Bar 图标  
   右侧竖向工具栏中的入口图标, 点击展开或收起 AgentDock 面板。

2. AgentDock 面板  
   展示当前项目的会话列表, 搜索框, provider 筛选, 状态筛选, 新建会话入口。

3. Terminal 集成  
   点击会话后, 在 IDE Terminal 打开或复用一个 tab, 切换到对应工作目录, 执行 provider 的 start/resume 命令。

4. Settings 页面  
   配置各 CLI 的可执行文件路径、默认模型参数、启动命令模板、恢复命令模板、会话存储位置。

5. Context Actions  
   在文件、文件夹、编辑器 tab、Terminal tab 上提供右键动作, 例如“Start Codex Session Here”或“Attach File to Agent Session”。

## 7. 核心概念

### 7.1 Project

JetBrains IDE 打开的项目或模块。AgentDock 的默认视角是“当前项目”。

### 7.2 Agent Session

一个可命名、可恢复、可搜索的 AI CLI 工作单元。它不等同于一个终端进程, 也不等同于一个 tab。一个会话至少包含:

- 会话名称
- 所属项目
- CLI provider
- 工作目录
- 状态
- 最近活动时间
- 恢复命令或 provider session id
- 关联文件
- 简短摘要

### 7.3 CLI Provider

一种可调用的 AI CLI 工具。v0.1 MVP 内置 Codex 和 Claude Code。Gemini CLI、OpenCode、Junie 等 provider 留到后续版本。

Provider 配置包括:

- 显示名称
- Logo
- 可执行命令
- 新建命令模板
- 恢复命令模板
- 检测命令
- 是否支持 session id
- 是否支持导入历史会话

### 7.4 Soft Resume

MVP 默认采用软恢复, 即保存会话元数据和 provider 的恢复信息。用户点击会话时, 插件在 Terminal 中重新打开对应 CLI 并执行 resume/continue/start 命令。

软恢复不保证原进程仍在运行, 但能保证用户不需要从一堆 CLI history 里手动翻找。

## 8. 用户场景

### 8.1 从当前项目创建一个 Codex 会话

1. 用户打开 PyCharm 项目 `phbs`
2. 点击右侧 `AgentDock`
3. 点击 `+` 或 `New Session`
4. 选择 `Codex`
5. 输入任务名, 例如“修复导出报告空数据”
6. 插件创建会话记录
7. 插件打开 Terminal tab, 切换到项目目录, 执行 Codex 启动命令
8. 会话出现在列表中, 状态为 `Active`

### 8.2 重启 IDE 后恢复 Claude Code 会话

1. 用户关闭 PyCharm
2. 再次打开项目
3. 右侧 `AgentDock` 仍然展示历史会话
4. 用户点击“经营推演页面重构”
5. 插件打开 Terminal tab
6. 插件执行 Claude Code 的 resume 命令
7. Terminal tab 标题更新为 `Claude Code · 经营推演页面重构`

### 8.3 一个项目下有多个 AI 会话

用户在同一个项目中可以同时存在多个 Codex 和 Claude Code 会话:

- Codex: 修复 pytest 失败
- Claude Code: 重构 dashboard 组件
- Codex: 接入 SiliconFlow provider
- Claude Code: 梳理架构文档

AgentDock 面板按状态、provider、最近活动排序, 让用户可以直接点击进入。

### 8.4 从文件树启动会话

1. 用户在 Project Tree 中选中 `docs/agent-v3`
2. 右键选择 `Start Agent Session Here`
3. 选择 `Claude Code`
4. 插件自动把工作目录设置为选中文件夹
5. 新会话自动关联该路径

### 8.5 CLI 未安装

1. 用户点击 `+ Codex`
2. 插件检测不到 `codex`
3. 会话不直接启动
4. 面板显示 `Missing CLI`
5. 用户可在 Settings 中设置路径或查看安装命令

## 9. 功能需求

### 9.1 P0 功能

| 编号 | 功能 | 说明 | 验收标准 |
| --- | --- | --- | --- |
| P0-01 | 右侧 Tool Window | 在 JetBrains IDE 右侧注册 `AgentDock` 工具窗口 | 可通过右侧 Tool Window Bar 打开和收起 |
| P0-02 | 会话列表 | 展示当前项目下所有未归档会话 | 显示名称、provider、状态、时间、摘要 |
| P0-03 | 新建会话 | 支持从面板新建 Codex 和 Claude Code 会话 | 新建后自动打开 Terminal tab 并执行命令 |
| P0-04 | 恢复会话 | 点击历史会话后打开 Terminal 并执行 resume 命令 | 不需要用户手动复制 session id |
| P0-05 | 会话命名 | 支持创建时命名和后续重命名 | 名称持久保存并显示在列表和 Terminal tab |
| P0-06 | 搜索 | 按名称、摘要、路径、provider 搜索 | 搜索结果实时过滤 |
| P0-07 | 归档 | 支持归档不常用会话 | 归档会话默认隐藏, 可切换查看 |
| P0-08 | 置顶 | 支持置顶关键会话 | 置顶会话排在列表前面 |
| P0-09 | Provider 配置 | 配置 Codex 和 Claude Code 的可执行文件和命令模板 | 两个内置 provider 可单独设置 |
| P0-10 | 本地持久化 | 保存项目会话元数据 | 重启 IDE 后会话列表仍可见 |

### 9.2 P1 功能

| 编号 | 功能 | 说明 | 验收标准 |
| --- | --- | --- | --- |
| P1-01 | 文件/文件夹右键启动 | 从 Project Tree 或编辑器上下文启动 AI 会话 | 会话自动关联选中路径 |
| P1-02 | Terminal tab 关联 | 将现有 Terminal tab 绑定到 Agent Session | 绑定后可在列表中显示为 Active |
| P1-03 | 会话详情页 | 展示关联文件、启动命令、恢复命令、最近终端摘要 | 用户可检查恢复信息 |
| P1-04 | Provider 自动检测 | 自动检测 Codex 和 Claude Code 是否可用 | 不可用时显示 Missing CLI |
| P1-05 | 最近活动摘要 | 从用户输入的任务名、命令模板或可解析日志中生成摘要 | 列表可快速区分任务 |
| P1-06 | 状态栏入口 | 在 IDE Status Bar 显示当前项目活跃 AI 会话数 | 点击可打开 AgentDock |
| P1-07 | 导入现有会话 | 手动导入 Codex 或 Claude Code 的历史 session id 或路径 | 导入后可以软恢复 |

### 9.3 P2 功能

| 编号 | 功能 | 说明 | 验收标准 |
| --- | --- | --- | --- |
| P2-01 | 可选进程保活 | 对高级用户支持后台守护或 tmux/zellij adapter | 默认关闭, 不影响 IDE 性能 |
| P2-02 | 更多 Provider | 支持 Gemini CLI、OpenCode、Junie 等 provider | 能复用同一套会话 metadata 和恢复流程 |
| P2-03 | ACP 外部 agent | 支持 Agent Client Protocol 类似的外部 agent 接入 | 能在统一面板展示外部 agent thread |
| P2-04 | 团队共享 | 共享会话摘要和任务记录, 不共享敏感终端内容 | 可按项目配置 |
| P2-05 | 云同步 | 跨设备同步会话索引 | 用户明确开启后生效 |
| P2-06 | 远程开发 | 支持 SSH/WSL/Docker Remote 场景 | Provider 检测和命令在远端执行 |
| P2-07 | Terminal toolbar 深度集成 | 在 JetBrains Terminal 的 AI agent 下拉中加入会话创建逻辑 | 与原生 Terminal UI 保持一致 |

## 10. 会话状态

| 状态 | 含义 | UI 表现 |
| --- | --- | --- |
| Active | 当前 IDE Terminal 中已有对应 tab 或刚刚启动 | 绿色状态点 |
| Restorable | 进程不一定存活, 但有恢复信息 | 蓝色状态点 |
| Missing CLI | provider 未检测到或路径失效 | 黄色警告 |
| Error | 最近一次恢复失败 | 红色状态点和错误信息 |
| Archived | 已归档 | 默认隐藏 |

## 11. 软恢复设计

### 11.1 为什么 MVP 不做默认进程保活

保持 Codex/Claude Code CLI 进程常驻可能带来以下问题:

- 占用更多内存和 CPU
- IDE 关闭后进程归属复杂
- 多项目多会话时难以控制资源
- 跨平台行为不一致
- 用户对“关闭 IDE 后是否还在运行 AI”容易产生误解

因此 MVP 应把“会话可见和可恢复”作为核心, 而不是“进程永远存活”。

### 11.2 恢复流程

1. 用户点击会话
2. 插件读取 provider 配置
3. 插件检查 CLI 是否可用
4. 插件创建或复用 Terminal tab
5. 插件切换到会话工作目录
6. 插件渲染并执行 resume 命令模板
7. 插件更新会话状态和最近活动时间

### 11.3 命令模板示例

```text
Codex start:
codex

Codex resume:
codex resume {{providerSessionId}}

Claude Code start:
claude

Claude Code resume:
claude --resume {{providerSessionId}}
```

实际命令以 Codex 和 Claude Code 的当前 CLI 文档为准。插件应允许用户自行修改模板, 避免 CLI 参数变化导致恢复失败。

## 12. 数据模型

### 12.1 Session

```json
{
  "id": "agd_20260708_001",
  "projectId": "phbs",
  "projectPath": "/Users/manruixie/code/phbs",
  "name": "经营推演页面重构",
  "provider": "claude-code",
  "status": "restorable",
  "cwd": "/Users/manruixie/code/phbs",
  "providerSessionId": "claude_01H...",
  "terminalTabId": null,
  "summary": "重构经营推演页面, 拆分 dashboard 组件并补充文档",
  "linkedFiles": [
    "docs/agent-v3/经营推演.md",
    "dashboard/src/pages/OperationDemo.vue"
  ],
  "createdAt": "2026-07-08T09:30:00+08:00",
  "updatedAt": "2026-07-08T17:42:00+08:00",
  "pinned": true,
  "archived": false
}
```

### 12.2 Provider

```json
{
  "id": "codex",
  "displayName": "Codex",
  "executable": "codex",
  "detectCommand": "codex --version",
  "startCommand": "codex",
  "resumeCommand": "codex resume {{providerSessionId}}",
  "supportsSessionId": true,
  "supportsImport": true,
  "enabled": true
}
```

### 12.3 Storage

MVP 推荐两层存储:

- 全局配置: provider 配置、用户偏好、命令模板
- 项目会话索引: 当前项目的 session metadata

候选位置:

- JetBrains PersistentStateComponent
- 项目 `.idea` 私有配置
- 用户本地应用数据目录

建议默认使用用户本地应用数据目录, 避免把个人会话信息提交到 Git。后续可提供“将会话索引保存到项目”的显式选项。

## 13. 技术方案

### 13.1 插件架构

推荐使用 Kotlin 开发 JetBrains 插件。

主要模块:

- `AgentDockToolWindowFactory`: 注册右侧 Tool Window
- `AgentSessionProjectService`: 管理当前项目会话状态
- `AgentSessionRepository`: 本地持久化
- `CLIProviderRegistry`: 管理 provider 配置
- `TerminalLauncher`: 调用 JetBrains Terminal 打开 tab 和执行命令
- `AgentSessionActions`: 注册菜单、右键、快捷动作
- `AgentDockSettingsConfigurable`: Settings 页面

### 13.2 JetBrains 平台能力

需要使用的插件平台能力:

- Tool Window: 右侧工具窗口和内容 tab
- Action System: 菜单、工具栏、右键动作
- Persistent State: 本地配置和项目状态保存
- Terminal integration: 尽可能使用官方或稳定 API 打开 Terminal
- Notification: CLI 缺失、恢复失败、命令执行错误时提示

### 13.3 Terminal 集成策略

优先级:

1. 使用 JetBrains 官方支持的 Terminal API 创建 session
2. 如果 API 不稳定, 则通过 Action System 打开 Terminal, 再以兼容方式注入命令
3. 如果无法自动注入命令, 至少打开 Terminal 并复制命令到剪贴板, 同时提示用户

MVP 的交付标准应以“能稳定恢复会话”为优先, 不追求完全复刻 JetBrains Terminal 内部 toolbar。

## 14. 交互设计

### 14.1 右侧 AgentDock 面板

面板信息层级:

1. 当前项目名
2. 搜索框
3. Provider 筛选
4. 状态筛选
5. 会话列表
6. 会话详情和操作

会话卡片展示:

- Provider logo
- 会话名
- 状态
- 最近活动
- 工作目录
- 简短摘要
- 操作按钮: Open, Rename, Pin, Archive

### 14.2 Terminal tab 命名

建议命名规则:

```text
{Provider} · {Session Name}
```

示例:

```text
Codex · 修复 pytest 失败
Claude Code · 经营推演页面重构
```

### 14.3 空状态

当前项目没有会话时, 展示:

- `New Codex Session`
- `New Claude Code Session`
- `Configure Providers`

不需要大段解释文案, 保持 IDE 工具面板的密度和效率。

### 14.4 错误状态

常见错误:

- CLI executable not found
- Resume command failed
- Session id missing
- Working directory no longer exists
- Terminal API unavailable

错误提示应包含:

- 发生了什么
- 哪个 provider 受影响
- 可执行的下一步, 例如打开 Settings 或复制命令

## 15. 竞品和相关产品比较

| 产品 | 优点 | 局限 | 对 AgentDock 的启发 |
| --- | --- | --- | --- |
| JetBrains Terminal AI Agents | 原生集成 IDE Terminal, 可从 Terminal 启动 Junie/Claude/Codex, 能保存 tab 名、cwd、shell history | 缺少项目级 AI 会话列表, 普通 terminal tab 无任务语义, 关闭 tab 会终止进程 | 应复用 JetBrains 工作台, 但补齐“会话索引层” |
| Warp | 现代终端体验强, 有 tab/pane/window 恢复和 AI 能力 | 不在 JetBrains IDE 内, 项目文件和编辑器上下文弱, 免费/付费入口对个人用户有摩擦 | 说明用户愿意为更好的终端工作流付费, 但 IDE 插件切入更贴近代码 |
| Zed External Agents / Threads | 有 Agent Panel, Threads Sidebar, Terminal Threads, 支持外部 agent thread 思路 | 用户需要切换到 Zed 生态, 不服务 JetBrains 用户 | “线程列表 + 外部 agent”是值得参考的产品方向 |
| Wave Terminal | AI-native terminal, workspace 组织能力, 文件预览和编辑体验较强 | 仍是独立终端, 不理解 JetBrains 项目模型和 IDE 操作 | Workspace 概念可借鉴, 但本产品应嵌入 IDE |
| Zellij / tmux | 进程保活和 session attach 能力强, 开源成熟 | 命令门槛高, 不是普通 IDE 用户想直接管理的产品形态, 不提供 AI 任务语义 | 可作为 P2 adapter, 不应成为 MVP 依赖 |
| Codex/Claude Code CLI 自带 resume | 官方能力, 最接近真实会话恢复 | 各家分散, 跨项目查找体验弱, 标题和上下文不统一 | 本产品应作为 provider resume 的可视化管理层 |

## 16. 市场判断

这个产品有明确市场机会, 但不适合一开始做成“泛终端”。

更好的切入点是:

- 先做 JetBrains 插件
- 面向重度 AI CLI 用户
- 解决一个高频、具体、痛感强的问题: 项目级多 AI 会话管理
- 不与 Warp、iTerm、Ghostty 等终端正面竞争
- 不与 Cursor、Zed、JetBrains AI 正面竞争模型能力

可商业化方向:

1. 免费版: 本地单项目会话管理, 基础 provider 配置
2. Pro 版: 多项目聚合、会话导入、高级搜索、模板、统计、远程开发支持
3. Team 版: 团队会话摘要共享、审计、项目知识沉淀

MVP 阶段优先验证:

- 用户是否愿意每天打开右侧 AgentDock
- 点击恢复是否显著降低找会话成本
- 是否会主动给每个 AI 任务命名
- 是否愿意把 Codex 和 Claude Code 会话都接入统一面板

## 17. 成功指标

| 指标 | 目标 |
| --- | --- |
| 首次创建会话成功率 | 大于 90% |
| 点击恢复成功率 | 大于 85% |
| 平均找回会话时间 | 小于 5 秒 |
| 单项目平均会话数 | 大于 3 |
| 7 日留存 | 大于 30% |
| 日均恢复次数 | 大于 5 次/活跃用户 |
| CLI 配置失败率 | 小于 10% |

## 18. 风险和应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| JetBrains Terminal API 不稳定 | 自动打开和注入命令失败 | 设计 TerminalLauncher adapter, 保留复制命令 fallback |
| 各 CLI resume 命令不同 | 恢复失败 | Provider 配置模板可编辑, 不硬编码 |
| 用户误以为会保活进程 | 预期落差 | UI 中区分 Active 和 Restorable, 文档明确软恢复 |
| CLI 更新破坏兼容 | 功能失效 | provider 版本检测和模板迁移 |
| 会话中包含敏感信息 | 隐私风险 | 默认只保存 metadata, 不保存完整终端输出 |
| Windows/macOS/Linux 差异 | 启动命令行为不一致 | 命令渲染层做 shell escaping 和平台适配 |
| Marketplace 审核 | 上架延迟 | 遵循 JetBrains 插件规范, 减少私有 API 依赖 |

## 19. 版本规划

### 19.1 Prototype

- HTML 原型
- PRD
- 关键交互验证

### 19.2 v0.1 MVP

- Tool Window Bar 入口
- 会话列表
- 新建/恢复/搜索/归档/置顶
- Codex 和 Claude Code provider
- 本地存储
- Settings 页面

### 19.3 v0.2

- Gemini CLI, OpenCode, Junie provider
- 文件/文件夹右键启动
- Terminal tab 关联
- 会话详情
- 扩展 provider 自动检测

### 19.4 v0.3

- 导入已有 CLI 会话
- 高级搜索
- 最近活动摘要
- Status Bar 入口
- 错误诊断面板

### 19.5 v1.0

- Marketplace 上架
- 跨 JetBrains IDE 家族支持
- 远程开发基础支持
- 可选 Pro 功能验证

## 20. 开放问题

1. Codex CLI 和 Claude Code CLI 是否都能稳定提供 machine-readable session id?
2. JetBrains Terminal API 中最稳妥的自动创建 tab 和发送命令方式是什么?
3. 会话索引默认存放在用户本地还是项目 `.idea`?
4. 是否需要为不同项目提供全局 Dashboard?
5. 是否允许用户把会话摘要同步到团队空间?
6. P2 的进程保活是否通过自研 daemon, 还是通过 tmux/zellij adapter?
7. 是否需要支持 Zed ACP 类似的标准协议, 以便未来接入更多外部 agent?

## 21. 参考资料

- JetBrains Terminal 文档: https://www.jetbrains.com/help/idea/terminal-emulator.html
- JetBrains Tool Window 插件文档: https://plugins.jetbrains.com/docs/intellij/tool-windows.html
- JetBrains Action System 插件文档: https://plugins.jetbrains.com/docs/intellij/action-system.html
- Zed External Agents 文档: https://zed.dev/docs/ai/external-agents
- Zed AI Overview: https://zed.dev/docs/ai/overview
- Warp Tab Configs 文档: https://docs.warp.dev/terminal/windows/tab-configs/
- Wave Terminal Workspaces 文档: https://docs.waveterm.dev/workspaces
- Wave Terminal 产品页: https://www.waveterm.dev/
