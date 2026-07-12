export const dashboardStyles = `
  :root {
    color-scheme: light dark;
    --ad-accent: #45df86;
    --ad-accent-soft: color-mix(in srgb, var(--ad-accent) 16%, transparent);
    --ad-line: color-mix(in srgb, var(--vscode-foreground) 15%, transparent);
    --ad-surface: color-mix(in srgb, var(--vscode-sideBar-background) 88%, var(--vscode-foreground) 12%);
    --ad-surface-raised: color-mix(in srgb, var(--vscode-sideBar-background) 82%, var(--vscode-foreground) 18%);
    --ad-muted: var(--vscode-descriptionForeground);
  }
  * { box-sizing: border-box; letter-spacing: 0; }
  html, body { min-height: 100%; }
  body {
    margin: 0;
    color: var(--vscode-foreground);
    background: var(--vscode-sideBar-background);
    font: var(--vscode-font-size)/1.45 var(--vscode-font-family);
  }
  button, input { font: inherit; }
  button { color: inherit; }
  #app { min-height: 100vh; }
  #overlay-root { position: fixed; inset: 0; z-index: 40; pointer-events: none; }

  .toolbar {
    position: sticky;
    top: 0;
    z-index: 10;
    display: grid;
    gap: 10px;
    padding: 12px;
    background: color-mix(in srgb, var(--vscode-sideBar-background) 96%, transparent);
    border-bottom: 1px solid var(--ad-line);
    backdrop-filter: blur(10px);
  }
  .search-wrap { position: relative; }
  .search-icon {
    position: absolute;
    top: 50%;
    left: 10px;
    width: 13px;
    height: 13px;
    border: 1.5px solid var(--ad-muted);
    border-radius: 50%;
    transform: translateY(-58%);
    pointer-events: none;
  }
  .search-icon::after {
    content: "";
    position: absolute;
    right: -5px;
    bottom: -3px;
    width: 6px;
    height: 1.5px;
    background: var(--ad-muted);
    transform: rotate(45deg);
    transform-origin: left center;
  }
  .search-input {
    width: 100%;
    height: 34px;
    padding: 0 10px 0 32px;
    color: var(--vscode-input-foreground);
    background: var(--vscode-input-background);
    border: 1px solid var(--vscode-input-border, var(--ad-line));
    border-radius: 6px;
    outline: none;
  }
  .search-input:focus { border-color: var(--vscode-focusBorder); box-shadow: 0 0 0 1px var(--vscode-focusBorder); }
  .filter-row { display: flex; align-items: center; min-width: 0; gap: 7px; }
  .filters { display: flex; flex: 1; min-width: 0; gap: 7px; overflow-x: auto; scrollbar-width: none; }
  .filters::-webkit-scrollbar { display: none; }
  .filter-button, .icon-button {
    display: inline-grid;
    flex: 0 0 auto;
    min-width: 34px;
    height: 34px;
    place-items: center;
    padding: 0 9px;
    background: var(--vscode-button-secondaryBackground);
    border: 1px solid transparent;
    border-radius: 6px;
    cursor: pointer;
  }
  .filter-button:hover, .icon-button:hover { background: var(--vscode-button-secondaryHoverBackground); }
  .filter-button.active { background: color-mix(in srgb, var(--vscode-button-background) 34%, transparent); border-color: var(--vscode-focusBorder); }
  .filter-button.all { display: flex; gap: 7px; font-weight: 600; }
  .filter-count {
    display: grid;
    min-width: 22px;
    height: 22px;
    place-items: center;
    padding: 0 5px;
    color: var(--vscode-badge-foreground);
    background: var(--vscode-badge-background);
    border-radius: 11px;
    font-size: 11px;
  }
  .provider-logo { display: block; width: 23px; height: 23px; object-fit: contain; }
  .icon-button { padding: 0; font-size: 20px; line-height: 1; }
  .icon-button.refreshing { animation: refresh-spin .75s linear infinite; }
  @keyframes refresh-spin { to { transform: rotate(360deg); } }

  .session-list { display: grid; gap: 10px; padding: 12px; }
  .session-card {
    min-width: 0;
    padding: 12px;
    background: var(--ad-surface);
    border: 1px solid var(--ad-line);
    border-radius: 7px;
    transition: border-color 140ms ease, background 140ms ease;
  }
  .session-card:hover { background: var(--ad-surface-raised); border-color: color-mix(in srgb, var(--vscode-foreground) 24%, transparent); }
  .session-header { display: grid; grid-template-columns: 27px minmax(0, 1fr) auto; align-items: center; gap: 9px; }
  .session-provider-logo { width: 27px; height: 27px; object-fit: contain; }
  .session-title {
    min-width: 0;
    overflow: hidden;
    color: var(--vscode-foreground);
    font-size: 14px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: default;
  }
  .session-title:hover { text-decoration: underline; text-decoration-color: color-mix(in srgb, var(--vscode-foreground) 45%, transparent); text-underline-offset: 3px; }
  .session-state { display: flex; align-items: center; gap: 6px; }
  .task-label { color: var(--ad-muted); font-size: 11px; white-space: nowrap; }
  .task-label.working { color: var(--ad-accent); }
  .task-label.ready { color: var(--vscode-notificationsInfoIcon-foreground); }
  .terminal-dot {
    position: relative;
    width: 11px;
    height: 11px;
    background: color-mix(in srgb, var(--ad-muted) 70%, transparent);
    border: 2px solid color-mix(in srgb, var(--vscode-sideBar-background) 72%, transparent);
    border-radius: 50%;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--ad-muted) 13%, transparent);
  }
  .terminal-dot.open { background: var(--ad-accent); box-shadow: 0 0 0 3px var(--ad-accent-soft); }
  .terminal-dot.open::after {
    content: "";
    position: absolute;
    inset: -5px;
    border: 1px solid var(--ad-accent);
    border-radius: 50%;
    animation: terminal-pulse 1.35s ease-in-out infinite;
  }
  @keyframes terminal-pulse { 0%, 100% { opacity: .15; transform: scale(.72); } 50% { opacity: .7; transform: scale(1.08); } }
  @media (prefers-reduced-motion: reduce) { .terminal-dot.open::after, .icon-button.refreshing, .live-text { animation: none !important; } }

  .metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
  .metric { display: grid; grid-template-columns: auto minmax(70px, 1fr) auto; align-items: center; min-width: 0; gap: 8px; }
  .metric-label { color: var(--ad-muted); font-size: 12px; white-space: nowrap; }
  .sparkline { display: block; width: 100%; min-width: 70px; height: 43px; overflow: visible; }
  .metric-value { min-width: 42px; overflow: hidden; color: var(--vscode-foreground); font-weight: 700; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
  .live-output {
    position: relative;
    height: 28px;
    margin: 11px 0 0 36px;
    overflow: hidden;
    color: var(--vscode-terminal-foreground);
    background: color-mix(in srgb, var(--vscode-terminal-background) 82%, transparent);
    border: 1px solid color-mix(in srgb, var(--vscode-terminal-border, var(--vscode-foreground)) 45%, transparent);
    border-radius: 6px;
  }
  .live-output::before {
    content: "";
    position: absolute;
    top: -5px;
    left: 20px;
    width: 8px;
    height: 8px;
    background: inherit;
    border-top: inherit;
    border-left: inherit;
    transform: rotate(45deg);
  }
  .live-track { position: absolute; inset: 0; overflow: hidden; }
  .live-text { position: absolute; top: 4px; left: 100%; white-space: nowrap; animation: ticker 10s linear infinite; }
  @keyframes ticker { from { transform: translateX(0); } to { transform: translateX(calc(-100% - 320px)); } }
  .session-footer { display: flex; align-items: center; gap: 8px; margin-top: 13px; }
  .updated { flex: 1; min-width: 0; overflow: hidden; color: var(--ad-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
  .action-button {
    height: 31px;
    padding: 0 11px;
    color: var(--vscode-button-secondaryForeground);
    background: var(--vscode-button-secondaryBackground);
    border: 1px solid transparent;
    border-radius: 5px;
    font-weight: 600;
    cursor: pointer;
  }
  .action-button:hover { background: var(--vscode-button-secondaryHoverBackground); }
  .action-button.yolo { color: var(--vscode-charts-orange); }
  .action-button:focus-visible, .filter-button:focus-visible, .icon-button:focus-visible { outline: 1px solid var(--vscode-focusBorder); outline-offset: 1px; }

  .empty-state { display: grid; min-height: 220px; place-items: center; padding: 28px; color: var(--ad-muted); text-align: center; }
  .empty-state strong { display: block; margin-bottom: 4px; color: var(--vscode-foreground); }
  .skeleton { display: grid; gap: 10px; padding: 12px; }
  .skeleton-card { height: 152px; background: linear-gradient(90deg, var(--ad-surface), var(--ad-surface-raised), var(--ad-surface)); background-size: 200% 100%; border: 1px solid var(--ad-line); border-radius: 7px; animation: skeleton 1.4s ease infinite; }
  @keyframes skeleton { to { background-position: -200% 0; } }

  .popup {
    position: fixed;
    pointer-events: auto;
    overflow: hidden;
    background: var(--vscode-editorWidget-background);
    border: 1px solid var(--vscode-widget-border, var(--ad-line));
    border-radius: 8px;
    box-shadow: 0 8px 28px color-mix(in srgb, #000 38%, transparent);
    clip-path: inset(0 round 8px);
  }
  .preview-popup { width: min(440px, calc(100vw - 16px)); max-height: min(500px, calc(100vh - 16px)); }
  .preview-header { padding: 11px 13px 10px; border-bottom: 1px solid var(--ad-line); }
  .preview-title { overflow: hidden; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
  .preview-meta { margin-top: 2px; color: var(--ad-muted); font-size: 11px; }
  .conversation { max-height: min(425px, calc(100vh - 90px)); overflow-y: auto; padding: 7px 10px 11px; background: color-mix(in srgb, var(--vscode-editorWidget-background) 88%, #000 12%); }
  .message-row { display: flex; align-items: flex-start; gap: 7px; padding: 6px 0; }
  .message-row.user { justify-content: flex-end; }
  .avatar { display: grid; flex: 0 0 30px; width: 30px; height: 30px; place-items: center; overflow: hidden; background: var(--ad-surface-raised); border: 1px solid var(--ad-line); border-radius: 6px; }
  .avatar img { width: 23px; height: 23px; object-fit: contain; }
  .user-avatar { position: relative; }
  .user-avatar::before { content: ""; position: absolute; top: 6px; width: 8px; height: 8px; background: var(--vscode-foreground); border-radius: 50%; opacity: .8; }
  .user-avatar::after { content: ""; position: absolute; bottom: 5px; width: 16px; height: 9px; background: var(--vscode-foreground); border-radius: 9px 9px 4px 4px; opacity: .8; }
  .bubble { position: relative; max-width: min(328px, calc(100% - 44px)); padding: 8px 11px; border-radius: 8px; white-space: pre-wrap; overflow-wrap: anywhere; }
  .assistant .bubble { background: var(--ad-surface-raised); border: 1px solid var(--ad-line); }
  .user .bubble { color: color-mix(in srgb, var(--vscode-foreground) 94%, #000 6%); background: color-mix(in srgb, var(--ad-accent) 28%, var(--vscode-editorWidget-background)); }
  .assistant .bubble::before, .user .bubble::before { content: ""; position: absolute; top: 10px; width: 8px; height: 8px; background: inherit; transform: rotate(45deg); }
  .assistant .bubble::before { left: -5px; border-left: 1px solid var(--ad-line); border-bottom: 1px solid var(--ad-line); }
  .user .bubble::before { right: -4px; }
  .conversation-notice { padding: 7px; color: var(--ad-muted); font-size: 11px; text-align: center; }

  .usage-popup { width: min(360px, calc(100vw - 16px)); }
  .usage-header { display: flex; align-items: center; gap: 8px; min-height: 46px; padding: 8px 11px; border-bottom: 1px solid var(--ad-line); }
  .usage-header strong { margin-right: auto; font-size: 14px; }
  .usage-pill { padding: 3px 9px; color: #08742f; background: #d8f5e4; border-radius: 999px; font-weight: 600; white-space: nowrap; }
  .usage-row { padding: 8px 11px 9px; border-bottom: 1px solid var(--ad-line); }
  .usage-row:last-child { border-bottom: 0; }
  .usage-line { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: baseline; gap: 8px; }
  .usage-label { font-weight: 650; white-space: nowrap; }
  .usage-reset { overflow: hidden; color: var(--ad-muted); text-overflow: ellipsis; white-space: nowrap; }
  .usage-left { font-weight: 700; white-space: nowrap; }
  .usage-bar { height: 4px; margin-top: 7px; overflow: hidden; background: color-mix(in srgb, var(--vscode-foreground) 18%, transparent); border-radius: 2px; }
  .usage-bar span { display: block; height: 100%; background: var(--vscode-progressBar-background); border-radius: inherit; }
  .usage-message { padding: 12px; color: var(--ad-muted); }
  .toast { position: fixed; right: 10px; bottom: 10px; left: 10px; z-index: 80; padding: 9px 11px; color: var(--vscode-notificationsErrorIcon-foreground); background: var(--vscode-notifications-background); border: 1px solid var(--vscode-notificationsErrorIcon-foreground); border-radius: 6px; box-shadow: 0 5px 20px color-mix(in srgb, #000 30%, transparent); }

  @media (max-width: 560px) {
    .metrics { grid-template-columns: 1fr; gap: 9px; }
    .task-label { display: none; }
    .session-footer { flex-wrap: wrap; }
    .updated { flex-basis: 100%; }
    .action-button { flex: 1; }
  }
`;
