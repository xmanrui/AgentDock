export const dashboardStyles = `
  :root {
    color-scheme: light dark;
    --ad-accent: #45df86;
    --ad-accent-soft: color-mix(in srgb, var(--ad-accent) 16%, transparent);
    --ad-line: color-mix(in srgb, var(--vscode-foreground) 15%, transparent);
    --ad-surface: color-mix(in srgb, var(--vscode-sideBar-background) 88%, var(--vscode-foreground) 12%);
    --ad-surface-raised: color-mix(in srgb, var(--vscode-sideBar-background) 82%, var(--vscode-foreground) 18%);
    --ad-muted: var(--vscode-descriptionForeground);
    --ad-card-line: #343b35;
    --ad-card-line-soft: #29302a;
    --ad-card-text: #eef2ec;
    --ad-card-text-dim: #889287;
    --ad-card-green: #68d982;
    --ad-card-green-soft: rgba(104, 217, 130, .13);
    --ad-card-trend-green: #4bde80;
    --ad-card-yellow: #e8b75d;
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
  .toolbar.usage-popup-active .search-wrap { visibility: hidden; }
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

  .session-list {
    display: flex;
    min-height: 0;
    flex-direction: column;
    gap: 8px;
    padding: 9px 10px 16px;
    overflow: auto;
  }
  .session-card {
    min-width: 0;
    padding: 9px 10px;
    color: var(--ad-card-text);
    background: rgba(255, 255, 255, .032);
    border: 1px solid var(--ad-card-line-soft);
    border-radius: 8px;
    cursor: default;
    font-size: 13px;
    transition: background .15s ease, border-color .15s ease, transform .15s ease;
  }
  .session-card:hover {
    background: rgba(255, 255, 255, .055);
    border-color: var(--ad-card-line);
    transform: translateY(-1px);
  }
  .session-header {
    display: grid;
    grid-template-columns: 24px minmax(0, 1fr) auto;
    align-items: center;
    gap: 9px;
  }
  .session-provider-state {
    position: relative;
    display: grid;
    width: 24px;
    height: 24px;
    place-items: center;
  }
  .session-provider-logo {
    display: block;
    width: 20px;
    height: 20px;
    object-fit: contain;
    transform-origin: center;
  }
  .session-provider-state.working .session-provider-logo {
    animation: provider-working 1.1s ease-in-out infinite;
  }
  .session-provider-state.ready::after {
    content: "";
    position: absolute;
    top: 0;
    right: 0;
    width: 7px;
    height: 7px;
    background: var(--ad-card-green);
    border: 1.5px solid var(--vscode-sideBar-background);
    border-radius: 999px;
    box-shadow: 0 0 0 2px var(--ad-card-green-soft);
  }
  @keyframes provider-working {
    0%, 100% { transform: scale(.86); opacity: .78; }
    50% { transform: scale(1.08); opacity: 1; }
  }
  .session-title {
    min-width: 0;
    overflow: hidden;
    color: var(--ad-card-text);
    font-size: 13px;
    font-weight: 770;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: default;
  }
  .session-state {
    display: inline-flex;
    width: 18px;
    height: 18px;
    align-items: center;
    justify-content: center;
    border-radius: 999px;
  }
  .terminal-dot {
    display: inline-flex;
    width: 18px;
    height: 18px;
    align-items: center;
    justify-content: center;
    border-radius: 999px;
  }
  .terminal-dot::before {
    content: "";
    display: block;
    width: 8px;
    height: 8px;
    background: #69736b;
    border-radius: 999px;
    box-shadow: 0 0 0 3px rgba(255, 255, 255, .045);
  }
  .terminal-dot.open::before {
    background: var(--ad-card-green);
    box-shadow: 0 0 0 3px var(--ad-card-green-soft);
    animation: terminal-pulse 1.35s ease-in-out infinite;
  }
  @keyframes terminal-pulse {
    0%, 100% { transform: scale(.96); box-shadow: 0 0 0 3px var(--ad-card-green-soft); }
    50% { transform: scale(1.1); box-shadow: 0 0 0 5px rgba(104, 217, 130, .22); }
  }
  @media (prefers-reduced-motion: reduce) {
    .terminal-dot.open::before, .session-provider-state.working .session-provider-logo, .icon-button.refreshing {
      animation: none !important;
    }
  }

  .metrics {
    display: grid;
    min-height: 48px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: center;
    gap: 12px;
    margin-top: 7px;
  }
  .metric {
    display: grid;
    min-width: 0;
    height: 48px;
    grid-template-columns: auto minmax(32px, 1fr) minmax(40px, auto);
    align-items: center;
    gap: 6px;
  }
  .metric-label {
    color: var(--ad-card-text-dim);
    font-size: 11px;
    line-height: 1;
    white-space: nowrap;
  }
  .sparkline {
    position: relative;
    display: block;
    width: 100%;
    max-width: 180px;
    min-width: 0;
    height: 48px;
    justify-self: end;
  }
  .sparkline svg {
    position: absolute;
    inset: 0;
    display: block;
    width: 100%;
    height: 48px;
    overflow: visible;
  }
  .sparkline-line {
    fill: none;
    stroke: var(--ad-card-trend-green);
    stroke-width: 3;
    stroke-linecap: round;
    stroke-linejoin: round;
    vector-effect: non-scaling-stroke;
  }
  .sparkline-area { stroke: none; pointer-events: none; }
  .sparkline-marker {
    position: absolute;
    display: block;
    width: 4px;
    height: 4px;
    background: var(--ad-card-trend-green);
    border-radius: 50%;
    transform: translate(-50%, -50%);
    pointer-events: none;
  }
  .sparkline-marker.missing { background: var(--ad-card-text-dim); opacity: .35; }
  .sparkline.unavailable .sparkline-line {
    stroke: var(--ad-card-text-dim);
    stroke-dasharray: 3 3;
    opacity: .5;
  }
  .metric-value {
    min-width: 40px;
    color: var(--ad-card-text);
    font-size: 12px;
    font-weight: 760;
    line-height: 1;
    text-align: right;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }
  .session-footer {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
  }
  .updated {
    min-width: 0;
    overflow: hidden;
    color: var(--ad-card-text-dim);
    font-size: 11px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .session-actions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; }
  .action-button {
    display: inline-flex;
    height: 28px;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 0 9px;
    color: #c0c8bf;
    background: rgba(255, 255, 255, .035);
    border: 1px solid var(--ad-card-line-soft);
    border-radius: 6px;
    font-size: 12px;
    font-weight: 760;
    white-space: nowrap;
    cursor: pointer;
    transition: background .15s ease, border-color .15s ease, color .15s ease, transform .15s ease;
  }
  .action-button:hover {
    color: var(--ad-card-text);
    background: rgba(255, 255, 255, .07);
    border-color: var(--ad-card-line);
  }
  .action-button.yolo {
    color: var(--ad-card-yellow);
    background: rgba(232, 183, 93, .08);
    border-color: rgba(232, 183, 93, .32);
  }
  .action-button.yolo:hover {
    color: #ffe0a0;
    background: rgba(232, 183, 93, .14);
    border-color: rgba(232, 183, 93, .52);
  }
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

  .usage-popup { width: min(360px, calc(100vw - 8px)); }
  .usage-row { padding: 2px 8px; border-bottom: 1px solid var(--ad-line); }
  .usage-row:last-child { border-bottom: 0; }
  .usage-line { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: baseline; gap: 6px; font-size: 12px; line-height: 14px; }
  .usage-label { font-weight: 650; white-space: nowrap; }
  .usage-reset { overflow: hidden; color: var(--ad-muted); text-overflow: ellipsis; white-space: nowrap; }
  .usage-left { font-weight: 700; white-space: nowrap; }
  .usage-bar { height: 3px; margin-top: 2px; overflow: hidden; background: color-mix(in srgb, var(--vscode-foreground) 18%, transparent); border-radius: 2px; }
  .usage-bar span { display: block; height: 100%; background: var(--vscode-progressBar-background); border-radius: inherit; }
  .usage-message { padding: 9px; color: var(--ad-muted); font-size: 11px; line-height: 1.25; }
  .toast { position: fixed; right: 10px; bottom: 10px; left: 10px; z-index: 80; padding: 9px 11px; color: var(--vscode-notificationsErrorIcon-foreground); background: var(--vscode-notifications-background); border: 1px solid var(--vscode-notificationsErrorIcon-foreground); border-radius: 6px; box-shadow: 0 5px 20px color-mix(in srgb, #000 30%, transparent); }

  @media (max-width: 300px) {
    .metrics { gap: 7px; }
    .metric { grid-template-columns: auto minmax(24px, 1fr) auto; gap: 4px; }
    .metric-label { font-size: 10px; }
  }
`;
