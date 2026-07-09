# AgentDock

AgentDock 是一个面向 JetBrains IDE 的项目级 AI CLI 会话管理插件。它把当前项目里的 Codex CLI、Claude Code CLI 会话集中展示在 IDE 右侧 Tool Window 中，让开发者可以像管理任务一样查看、新建、恢复、搜索、置顶和归档 AI 会话。

AgentDock 不替代 JetBrains Terminal，也不负责保活 CLI 进程。它保存的是会话元数据和 provider 恢复信息，并在需要时通过 IDE Terminal 执行对应的 start/resume 命令，实现轻量的 soft resume。

## 功能概览

- 右侧 `AgentDock` Tool Window 入口
- 当前项目下的 AI CLI 会话列表
- Codex 和 Claude Code provider 默认支持
- 新建会话并打开 JetBrains Terminal 执行启动命令
- 点击历史会话后执行 provider resume 命令
- 自动发现本机 Codex / Claude Code 的项目相关历史会话
- 会话搜索、provider/status 筛选、重命名、置顶、归档
- Provider executable、detect/start/resume command template 可配置
- 项目级会话 metadata 本地持久化
- Terminal API 异常时复制命令到剪贴板作为 fallback

## 支持范围

| 项目 | 当前状态 |
| --- | --- |
| 插件平台 | IntelliJ Platform / JetBrains IDE |
| 目标 IDE | PyCharm, IntelliJ IDEA, WebStorm, PhpStorm 等 |
| since build | `252` |
| 内置 provider | Codex, Claude Code |
| UI | JCEF Tool Window 主界面，JCEF 不可用时使用 Swing fallback |
| 存储 | IntelliJ `PersistentStateComponent` |
| 终端集成 | JetBrains Terminal plugin |
| 版本 | `0.1.11` |

## 项目结构

```text
.
├── agentdock-plugin/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/agentdock/
│       │   │   ├── actions/        # Tools 菜单和会话动作
│       │   │   ├── model/          # AgentSession, CLIProvider 等模型
│       │   │   ├── notification/   # IDE 通知封装
│       │   │   ├── service/        # 会话服务、provider 检测、本地发现
│       │   │   ├── storage/        # 持久化状态、过滤和迁移
│       │   │   ├── terminal/       # Terminal 启动、命令渲染、fallback
│       │   │   ├── ui/             # Tool Window、HTML renderer、设置页、弹窗
│       │   │   └── util/
│       │   ├── java/com/agentdock/ui/
│       │   └── resources/
│       │       ├── META-INF/plugin.xml
│       │       └── icons/
│       └── test/kotlin/com/agentdock/
├── docs/
│   ├── AgentDock-PRD.md
│   ├── AgentDock-IMPLEMENTATION-PLAN.md
│   ├── TECH-STACK.md
│   └── agentdock-prototype.html
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── gradlew
```

## Provider 默认配置

| Provider | Executable | Detect command | Start command | Resume command |
| --- | --- | --- | --- | --- |
| Codex | `codex` | `codex --version` | `{{executable}}` | `{{executable}} resume {{providerSessionId?}}` |
| Claude Code | `claude` | `claude --version` | `{{executable}} --ide --name {{sessionName}}` | `{{executable}} --resume {{providerSessionId?}} --ide` |

命令模板支持的变量包括:

- `{{executable}}`
- `{{providerSessionId}}` / `{{providerSessionId?}}`
- `{{sessionName}}`
- `{{cwd}}`
- `{{projectPath}}`

带 `?` 的变量是可选变量，值为空时会被移除。Provider 设置可以在 IDE 的 `Tools > AgentDock Settings` 中修改。

## 环境要求

- JetBrains IDE `2025.2+`，对应 IntelliJ build `252+`
- JDK 21 推荐用于本地 Gradle/IntelliJ Platform 构建任务
- Gradle wrapper 已包含，无需单独安装 Gradle
- 使用 Codex 功能时需要本机可执行 `codex`
- 使用 Claude Code 功能时需要本机可执行 `claude`

如果本机默认 Java 版本较低，可以显式指定:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :agentdock-plugin:test
```

## 本地开发

运行开发 IDE:

```bash
./gradlew :agentdock-plugin:runIde
```

运行测试:

```bash
./gradlew :agentdock-plugin:test
```

验证插件项目配置:

```bash
./gradlew :agentdock-plugin:verifyPluginProjectConfiguration
```

验证插件结构:

```bash
./gradlew :agentdock-plugin:verifyPluginStructure
```

运行 Plugin Verifier:

```bash
./gradlew :agentdock-plugin:verifyPlugin
```

构建插件包:

```bash
./gradlew :agentdock-plugin:buildPlugin
```

构建产物位于:

```text
agentdock-plugin/build/distributions/
```

## 安装插件包

1. 执行构建:

```bash
./gradlew :agentdock-plugin:buildPlugin
```

2. 在 JetBrains IDE 中打开:

```text
Settings / Preferences > Plugins > 齿轮菜单 > Install Plugin from Disk...
```

3. 选择 `agentdock-plugin/build/distributions/` 下生成的 zip 文件。

4. 重启 IDE。

安装后可以通过以下入口打开:

- 右侧 Tool Window Bar 的 `AgentDock`
- `Tools > Open AgentDock`
- `Tools > AgentDock Settings`

## 使用方式

### 新建会话

1. 打开任意 JetBrains 项目。
2. 打开右侧 `AgentDock` 面板。
3. 点击新建会话按钮。
4. 选择 `Codex` 或 `Claude Code`。
5. 填写会话名称、工作目录、摘要和可选的 provider session id。
6. AgentDock 会保存会话 metadata，并在 IDE Terminal 中执行对应 start command。

### 恢复会话

1. 在 `AgentDock` 面板中点击已有会话。
2. AgentDock 根据 provider、工作目录和 provider session id 渲染 resume command。
3. 插件打开 Terminal tab 并发送命令。
4. 如果 Terminal API 调用失败，插件会打开 Terminal 并把命令复制到剪贴板。

### 管理会话

会话支持:

- 搜索
- provider/status 筛选
- 重命名
- 置顶
- 归档 / 取消归档

## 本地会话发现

AgentDock 会根据当前项目路径发现本机已有会话:

- Codex: 读取 `~/.codex/sessions` 和 `~/.codex/session_index.jsonl`
- Claude Code: 读取 `~/.claude/projects`

只会导入工作目录属于当前 JetBrains 项目的会话，避免跨项目历史混杂。

## 数据与隐私

- AgentDock 默认只保存会话 metadata。
- 不保存完整终端输出。
- 不托管或读取用户 AI API key。
- 不默认同步到云端。
- Provider 设置是 application-level 状态。
- 项目会话 metadata 保存在项目 workspace 状态中。

## 已知限制

- 当前只内置 Codex 和 Claude Code。
- Gemini CLI、OpenCode、Junie 等 provider 尚未进入 v0.1 范围。
- 不保证 IDE 关闭后 CLI 进程仍然存活。
- `providerSessionId` 在新建会话时可能需要手动填写。
- 文件/文件夹右键启动、Terminal tab 绑定、会话详情页仍属于后续功能。
- JCEF 不可用时会显示简化 Swing fallback。

## 开源协议

本项目基于 MIT License 开源，详见 `LICENSE`。

## 参考文档

- `docs/AgentDock-PRD.md`
- `docs/AgentDock-IMPLEMENTATION-PLAN.md`
- `docs/TECH-STACK.md`
- `docs/agentdock-prototype.html`
