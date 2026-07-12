# AgentDock for VS Code

> 让 AI 会话，各就各位。

AgentDock 是一个本地优先、面向 VS Code 的项目级 AI CLI 会话仪表盘。它会自动发现当前工作区关联的 Codex CLI、Claude Code 和 Gemini CLI 会话，并集中提供查看、恢复、YOLO、置顶、筛选、用量分析和终端任务状态监控。

本目录是 JetBrains AgentDock 插件的 VS Code 移植版本，使用 TypeScript、VS Code Extension API、Webview View、esbuild 和 Vitest 实现。

## 功能

- 在 Activity Bar 提供 AgentDock 会话视图，可移动到 VS Code Secondary Side Bar 作为右侧全高面板
- 自动发现当前工作区的 Codex、Claude Code 和 Gemini CLI 本地历史
- 搜索、Provider 筛选、会话计数、手动刷新和可配置的后台刷新
- 会话卡片展示最近 7 天 Token Usage、Avg. Time、历史 Token 总量和当天平均响应秒数
- 标题悬停或键盘聚焦时显示聊天气泡预览：AI 在左、用户在右、AI 使用 Provider Logo
- Provider Logo 悬停或聚焦时显示项目 Token、5 小时额度、周额度、重置时间和可用重置次数
- `Open`、Provider 专属 `YOLO`、`Pin / Unpin`
- 原生 VS Code Terminal 保留静态 Provider Logo；活动标签标题用旋转图标表示 Working、绿色圆点表示 Ready，Idle 恢复原标题
- 工作期间过滤 Shell Integration 流式输出，并在对应会话卡片中从右向左滚动展示
- 关闭终端或 CLI 退出后立即清除卡片绿点；Unix 额外使用退出标记兜底
- 支持多个同会话终端实例并聚合状态
- 自动查找 PATH、常见安装目录、NVM/FNM/Volta/ASDF/Mise 和 macOS App Bundle 中真正可运行的 CLI
- Provider executable 与 detect/start/resume/YOLO command template 均可在 Settings 中配置
- 会话元数据、Pin 和指标缓存使用 workspace state 持久化；完整聊天和终端输出不额外保存

## 卡片状态

| 显示 | 含义 |
| --- | --- |
| 灰点 | 没有由 AgentDock 启动且仍活动的对应 CLI |
| 闪烁绿点 | 至少一个对应 CLI 仍活动 |
| Working | Provider 历史记录报告新任务已开始 |
| Ready | Provider 历史记录报告任务已完成，等待查看 |
| Idle | 未工作，或 Ready 终端已被查看 |

卡片绿点只表示 CLI 是否仍活动，Working/Ready 才表示模型任务状态。

终端标题状态只使用 VS Code 原生标签能力：当前活动终端会实时更新；后台终端保留最新任务状态，并在用户切换到该标签时同步标题。完成任务时若终端原本就在前台，Ready 会保留到用户切走并重新查看该标签。

## Provider 支持

| 能力 | Codex | Claude Code | Gemini CLI |
| --- | --- | --- | --- |
| 项目会话发现 | 是 | 是 | 是 |
| 聊天预览 | 是 | 是 | 是 |
| 7 天 Token/Avg. Time | 是 | 是 | 是 |
| Open/YOLO | 是 | 是 | 是 |
| Working/Ready 监听 | 是 | 是 | 是 |
| 套餐额度 | 是 | 是 | 暂不支持 |

套餐额度只在悬停 Provider Logo 时按需加载。Codex 调用本机 `codex app-server --stdio`；Claude 使用本机可用 OAuth 凭据请求配置端点；Gemini 仅显示项目本地指标。

## 安装

从源码构建 VSIX：

```bash
cd AgentDock-VSCode
npm install
npm run package
```

产物：

```text
dist/agentdock-vscode.vsix
```

可在 VS Code 的 `Extensions: Install from VSIX...` 中安装，或执行：

```bash
code --install-extension dist/agentdock-vscode.vsix
```

打开 AgentDock Activity Bar 图标即可使用。需要固定在右侧时，可将 AgentDock View Container 移动到 Secondary Side Bar。

## 默认命令

| Provider | Resume | YOLO Resume |
| --- | --- | --- |
| Codex | `{{executable}} resume {{providerSessionId?}}` | `{{executable}} resume --dangerously-bypass-approvals-and-sandbox {{providerSessionId?}}` |
| Claude Code | `{{executable}} --resume {{providerSessionId?}} --ide` | `{{executable}} --resume {{providerSessionId?}} --ide --dangerously-skip-permissions` |
| Gemini CLI | `{{executable}} --resume {{providerSessionId?}}` | `{{executable}} --resume {{providerSessionId?}} --yolo` |

支持变量：`{{executable}}`、`{{providerSessionId}}`、`{{providerSessionId?}}`、`{{sessionName}}`、`{{cwd}}`、`{{projectPath}}`。带 `?` 的变量为空时会被移除。

YOLO 会绕过对应 CLI 的权限确认或沙箱限制，只应在可信工作区中使用。

## 本地数据来源

- Codex：`~/.codex/sessions`、`~/.codex/session_index.jsonl`
- Claude Code：`~/.claude/projects`
- Gemini CLI：`~/.gemini/tmp/<project_hash>/chats`

AgentDock 只导入工作目录属于当前工作区的会话。预览和指标直接读取 Provider 文件，并按文件修改时间、长度和日期缓存。

## 开发与测试

要求：Node.js 20+、VS Code 1.95+。

```bash
npm run check             # TypeScript + ESLint
npm test                  # Vitest 单元/服务测试
npm run test:coverage     # 覆盖率
npm run test:integration  # 隔离 VS Code Extension Host + 原生终端生命周期
npm run build             # Extension + Webview bundle
npm run package           # 完整校验并生成 VSIX
```

Webview 视觉夹具：

```text
test/webview/fixture.html
```

## VS Code 平台差异

VS Code Extension API 不允许扩展修改原生 Terminal Tab 的 DOM、动态缩放标签图标、绘制图标角标，或在终端标题上方创建任意浮层。因此：

- Provider Logo 仍显示在原生 Terminal Tab，但保持静态。
- 当前活动标签标题在 Working 时显示旋转图标、Ready 时显示绿色圆点、Idle 时恢复原标题；后台标签在被选中时同步。
- Working/Ready 同时显示在 AgentDock 会话卡片的 Provider Logo 上，便于比较多个后台终端。
- 任务解析、Ready 被查看后复位、CLI 退出和多终端聚合逻辑与 JetBrains 版本一致。

这是公开 API 能实现的最接近等价呈现，详细记录见 `docs/验收记录/02-踩坑与平台差异.md`。

## 隐私

- 所有会话发现、预览、指标计算和终端状态解析都在本地完成。
- 不上传完整会话或终端输出，不提供 AgentDock 云服务，不采集 telemetry。
- Claude 套餐额度查询仅使用本机凭据访问配置的 Anthropic 端点。
- Codex 套餐额度查询不读取 API key，而是调用本机 Codex app server。

## License

MIT
