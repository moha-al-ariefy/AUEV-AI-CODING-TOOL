import * as vscode from "vscode";
import { AuevAiClient } from "./aiClient";

export class AuevChatViewProvider implements vscode.WebviewViewProvider {
  public static readonly viewType = "auev.chatView";
  private _view?: vscode.WebviewView;

  constructor(private readonly _extensionUri: vscode.Uri) {}

  public resolveWebviewView(
    webviewView: vscode.WebviewView,
    context: vscode.WebviewViewResolveContext,
    _token: vscode.CancellationToken
  ) {
    this._view = webviewView;

    webviewView.webview.options = {
      enableScripts: true,
      localResourceRoots: [this._extensionUri]
    };

    webviewView.webview.html = this._getHtmlForWebview(webviewView.webview);

    webviewView.webview.onDidReceiveMessage(async (data) => {
      switch (data.type) {
        case "sendMessage": {
          await this.handleUserMessage(data.text);
          break;
        }
        case "quickAction": {
          await this.handleQuickAction(data.action);
          break;
        }
        case "applyCode": {
          await this.applyCodeToEditor(data.code, data.mode);
          break;
        }
        case "undoAction": {
          await vscode.commands.executeCommand("undo");
          this._view?.webview.postMessage({ type: "undoSuccess" });
          break;
        }
        case "openSettings": {
          await vscode.commands.executeCommand("workbench.action.openSettings", "auev");
          break;
        }
        case "changeModel": {
          await vscode.workspace.getConfiguration("auev").update("model", data.model, vscode.ConfigurationTarget.Global);
          break;
        }
      }
    });
  }

  public async postExternalAction(action: string) {
    if (this._view) {
      await this.handleQuickAction(action);
    }
  }

  private async handleUserMessage(userPrompt: string) {
    if (!this._view || !userPrompt.trim()) return;

    this._view.webview.postMessage({
      type: "addMessage",
      message: { text: userPrompt, isUser: true }
    });

    this._view.webview.postMessage({ type: "setLoading", loading: true, status: "AUEV is thinking..." });

    const editor = vscode.window.activeTextEditor;
    const document = editor?.document;
    const selection = editor?.selection;
    const selectedText = (!selection?.isEmpty && document) ? document.getText(selection) : "";
    const fileContent = document ? document.getText() : "";
    const fileName = document?.fileName.split(/[/\\]/).pop() || "";
    const languageId = document?.languageId || "";

    const contextCode = selectedText || fileContent;

    try {
      const response = await AuevAiClient.chat(userPrompt, contextCode, fileName, languageId);
      this._view.webview.postMessage({ type: "setLoading", loading: false });
      this._view.webview.postMessage({
        type: "addMessage",
        message: { text: response, isUser: false }
      });
    } catch (err: any) {
      this._view.webview.postMessage({ type: "setLoading", loading: false });
      this._view.webview.postMessage({
        type: "addMessage",
        message: { text: `⚠️ Error: ${err.message || err}`, isUser: false }
      });
    }
  }

  private async handleQuickAction(action: string) {
    if (!this._view) return;

    const editor = vscode.window.activeTextEditor;
    if (!editor) {
      this._view.webview.postMessage({
        type: "addMessage",
        message: { text: "⚠️ No file open in editor to perform this action.", isUser: false }
      });
      return;
    }

    const document = editor.document;
    const selection = editor.selection;
    const selectedText = (!selection.isEmpty) ? document.getText(selection) : "";
    const code = selectedText || document.getText();
    const fileName = document.fileName.split(/[/\\]/).pop() || "Untitled";
    const languageId = document.languageId;

    let actionLabel = "";
    let runner: () => Promise<string>;

    switch (action) {
      case "audit":
        actionLabel = "🛡️ Security Audit";
        runner = () => AuevAiClient.runAudit(fileName, languageId, code);
        break;
      case "explain":
        actionLabel = "💡 Explain Code";
        runner = () => AuevAiClient.runExplain(fileName, languageId, code);
        break;
      case "refactor":
        actionLabel = "⚡ Refactor Code";
        runner = () => AuevAiClient.runRefactor(fileName, languageId, code);
        break;
      case "tests":
        actionLabel = "🧪 Generate Tests";
        runner = () => AuevAiClient.runGenerateTests(fileName, languageId, code);
        break;
      case "sanitize":
        actionLabel = "🔒 Sanitize Secrets";
        runner = () => AuevAiClient.runSanitize(fileName, languageId, code);
        break;
      default:
        return;
    }

    this._view.webview.postMessage({
      type: "addMessage",
      message: { text: `Running ${actionLabel} on ${fileName}...`, isUser: true }
    });

    this._view.webview.postMessage({ type: "setLoading", loading: true, status: `Executing ${actionLabel}...` });

    try {
      const response = await runner();
      this._view.webview.postMessage({ type: "setLoading", loading: false });
      this._view.webview.postMessage({
        type: "addMessage",
        message: { text: response, isUser: false }
      });
    } catch (err: any) {
      this._view.webview.postMessage({ type: "setLoading", loading: false });
      this._view.webview.postMessage({
        type: "addMessage",
        message: { text: `⚠️ Error during ${actionLabel}: ${err.message || err}`, isUser: false }
      });
    }
  }

  private async applyCodeToEditor(rawCode: string, mode: "smart" | "caret" | "file") {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
      vscode.window.showWarningMessage("AUEV: No active editor to apply code into.");
      return;
    }

    const cleanCode = this.extractCode(rawCode);

    if (mode === "caret") {
      const pos = editor.selection.active;
      await editor.edit((editBuilder) => {
        editBuilder.insert(pos, cleanCode);
      });
      vscode.window.showInformationMessage("AUEV: Injected code at caret.");
    } else if (mode === "file") {
      const fullRange = new vscode.Range(
        editor.document.positionAt(0),
        editor.document.positionAt(editor.document.getText().length)
      );
      await editor.edit((editBuilder) => {
        editBuilder.replace(fullRange, cleanCode);
      });
      vscode.window.showInformationMessage("AUEV: Replaced file with generated code.");
    } else {
      // Smart: if selection exists, replace selection; otherwise insert at caret
      if (!editor.selection.isEmpty) {
        await editor.edit((editBuilder) => {
          editBuilder.replace(editor.selection, cleanCode);
        });
        vscode.window.showInformationMessage("AUEV: Replaced selection with generated code.");
      } else {
        const pos = editor.selection.active;
        await editor.edit((editBuilder) => {
          editBuilder.insert(pos, cleanCode);
        });
        vscode.window.showInformationMessage("AUEV: Injected code at caret.");
      }
    }
  }

  private extractCode(text: string): string {
    const match = /```(?:[a-zA-Z0-9_-]*)?\r?\n([\s\S]*?)```/.exec(text);
    if (match) {
      return match[1].trim();
    }
    return text.replace(/^```[a-zA-Z0-9_-]*/, "").replace(/```$/, "").trim();
  }

  private _getHtmlForWebview(webview: vscode.Webview): string {
    const config = vscode.workspace.getConfiguration("auev");
    const currentModel = config.get<string>("model", "gpt-4o");
    const isParanoid = config.get<boolean>("paranoidMode", true);

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    :root {
      --bg: #1e1e1e;
      --card-bg: #252526;
      --bubble-user: #2b313a;
      --accent: #2f80ed;
      --text: #cccccc;
      --text-bright: #ffffff;
      --border: #333333;
      --pill-bg: #2a2d34;
      --pill-border: #3c404b;
      --green: #81c784;
      --code-string: #ce9178;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      font-size: 13px;
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
    }
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      background: var(--card-bg);
      border-bottom: 1px solid var(--border);
    }
    .logo-container {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .logo-text {
      font-weight: bold;
      color: var(--accent);
      font-size: 14px;
    }
    .badge {
      font-size: 10px;
      font-weight: 600;
      padding: 2px 7px;
      border-radius: 10px;
      background: #1b382b;
      color: var(--green);
      border: 1px solid #2e7d32;
    }
    .header-icons {
      display: flex;
      gap: 8px;
    }
    .icon-btn {
      background: transparent;
      border: none;
      color: #888;
      cursor: pointer;
      font-size: 14px;
      padding: 2px 5px;
      border-radius: 4px;
    }
    .icon-btn:hover { color: #fff; background: rgba(255,255,255,0.1); }
    .quick-actions {
      display: flex;
      gap: 6px;
      padding: 8px 10px;
      overflow-x: auto;
      background: var(--bg);
      border-bottom: 1px solid var(--border);
      scrollbar-width: none;
    }
    .quick-actions::-webkit-scrollbar { display: none; }
    .action-pill {
      background: var(--pill-bg);
      border: 1px solid var(--pill-border);
      color: #e1e1e1;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 9px;
      border-radius: 12px;
      cursor: pointer;
      white-space: nowrap;
      transition: background 0.15s ease;
    }
    .action-pill:hover { background: #373b45; border-color: var(--accent); }
    .chat-history {
      flex: 1;
      overflow-y: auto;
      padding: 12px 10px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .message {
      display: flex;
      flex-direction: column;
      max-width: 95%;
    }
    .message.user { align-self: flex-end; }
    .message.bot { align-self: flex-start; }
    .author-tag {
      font-size: 11px;
      font-weight: bold;
      color: #888;
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .bubble {
      padding: 9px 12px;
      border-radius: 8px;
      line-height: 1.5;
      word-break: break-word;
      white-space: pre-wrap;
    }
    .message.user .bubble {
      background: var(--bubble-user);
      color: #e1e1e1;
    }
    .message.bot .bubble {
      background: transparent;
      color: var(--text);
      padding: 0;
    }
    .code-card {
      background: #181818;
      border: 1px solid var(--border);
      border-radius: 6px;
      margin-top: 8px;
      overflow: hidden;
    }
    .code-card-header {
      background: #252526;
      padding: 4px 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 11px;
      color: #aaa;
      font-weight: 600;
    }
    .code-content {
      padding: 10px 12px;
      font-family: 'Consolas', 'Courier New', monospace;
      font-size: 12px;
      color: var(--code-string);
      overflow-x: auto;
      white-space: pre;
    }
    .code-footer {
      display: flex;
      gap: 6px;
      padding: 6px 8px;
      background: #202020;
      border-top: 1px solid var(--border);
    }
    .card-btn {
      font-size: 11px;
      font-weight: 600;
      padding: 4px 8px;
      border-radius: 4px;
      cursor: pointer;
      border: none;
    }
    .btn-apply { background: var(--accent); color: #fff; flex: 1; }
    .btn-apply:hover { background: #236bc7; }
    .btn-caret { background: transparent; border: 1px solid #555; color: #ddd; }
    .btn-caret:hover { background: #333; }
    .btn-copy { background: transparent; color: #fff; cursor: pointer; border: none; font-size: 11px; }
    .btn-copy:hover { text-decoration: underline; }
    .input-container {
      padding: 10px;
      background: var(--bg);
      border-top: 1px solid var(--border);
    }
    .input-card {
      background: var(--card-bg);
      border: 1px solid #3e3e42;
      border-radius: 8px;
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    textarea {
      background: transparent;
      border: none;
      color: #e1e1e1;
      font-family: inherit;
      font-size: 13px;
      resize: none;
      outline: none;
      min-height: 38px;
      max-height: 180px;
      line-height: 1.4;
    }
    .input-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    select {
      background: #333;
      color: #fff;
      border: none;
      padding: 3px 8px;
      border-radius: 10px;
      font-size: 11px;
      outline: none;
      cursor: pointer;
    }
    .send-btn {
      background: var(--accent);
      color: #fff;
      font-size: 12px;
      font-weight: bold;
      border: none;
      padding: 4px 12px;
      border-radius: 4px;
      cursor: pointer;
    }
    .send-btn:hover { background: #236bc7; }
    .spinner {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #aaa;
      padding-top: 4px;
    }
    .dot { width: 6px; height: 6px; background: var(--accent); border-radius: 50%; animation: pulse 1s infinite alternate; }
    @keyframes pulse { from { opacity: 0.3; transform: scale(0.8); } to { opacity: 1; transform: scale(1.2); } }
  </style>
</head>
<body>
  <div class="header">
    <div class="logo-container">
      <span class="logo-text">AUEV</span>
      <span class="badge">${isParanoid ? "🛡️ Paranoid" : "⚡ Standard"}</span>
    </div>
    <div class="header-icons">
      <button class="icon-btn" id="clearBtn" title="Clear Chat">🗑️</button>
      <button class="icon-btn" id="settingsBtn" title="AUEV Settings">⚙</button>
    </div>
  </div>

  <div class="quick-actions">
    <button class="action-pill" data-action="audit">🛡️ Audit</button>
    <button class="action-pill" data-action="explain">💡 Explain</button>
    <button class="action-pill" data-action="refactor">⚡ Refactor</button>
    <button class="action-pill" data-action="tests">🧪 Tests</button>
    <button class="action-pill" data-action="sanitize">🔒 Sanitize</button>
  </div>

  <div class="chat-history" id="chatHistory">
    <div class="message bot">
      <div class="author-tag">AUEV</div>
      <div class="bubble">Hello! I am AUEV (AI Unified Editor Vision). Security-first pair programming activated. Ready to code safely.</div>
    </div>
  </div>

  <div class="input-container">
    <div class="input-card">
      <textarea id="promptInput" placeholder="Ask AUEV or run quick actions (Enter to send)..." rows="2"></textarea>
      <div class="input-toolbar">
        <select id="modelSelector">
          <option value="gpt-4o" ${currentModel === "gpt-4o" ? "selected" : ""}>gpt-4o</option>
          <option value="gpt-4o-mini" ${currentModel === "gpt-4o-mini" ? "selected" : ""}>gpt-4o-mini</option>
          <option value="claude-3-5-sonnet-20240620" ${currentModel.includes("claude") ? "selected" : ""}>claude-3.5-sonnet</option>
          <option value="llama-3.3-70b-versatile" ${currentModel.includes("llama") ? "selected" : ""}>llama-3.3-70b</option>
        </select>
        <button class="send-btn" id="sendBtn">Send ⏎</button>
      </div>
    </div>
  </div>

  <script>
    const vscode = acquireVsCodeApi();
    const chatHistory = document.getElementById('chatHistory');
    const promptInput = document.getElementById('promptInput');
    const sendBtn = document.getElementById('sendBtn');
    const clearBtn = document.getElementById('clearBtn');
    const settingsBtn = document.getElementById('settingsBtn');
    const modelSelector = document.getElementById('modelSelector');

    function scrollToBottom() {
      chatHistory.scrollTop = chatHistory.scrollHeight;
    }

    function appendMessage(text, isUser) {
      const msgDiv = document.createElement('div');
      msgDiv.className = 'message ' + (isUser ? 'user' : 'bot');

      if (!isUser) {
        const tag = document.createElement('div');
        tag.className = 'author-tag';
        tag.textContent = 'AUEV';
        msgDiv.appendChild(tag);
      }

      const hasCode = !isUser && text.includes('\`\`\`');
      if (hasCode) {
        const parts = text.split(/(\`\`\`[\\s\\S]*?\`\`\`)/g);
        parts.forEach(part => {
          if (part.startsWith('\`\`\`') && part.endsWith('\`\`\`')) {
            const langMatch = part.match(/^\`\`\`([a-zA-Z0-9_-]+)/);
            const lang = langMatch ? langMatch[1] : 'CODE';
            const code = part.replace(/^\`\`\`[a-zA-Z0-9_-]*\\r?\\n?/, '').replace(/\\r?\\n?\`\`\`$/, '');

            const card = document.createElement('div');
            card.className = 'code-card';

            const cardHeader = document.createElement('div');
            cardHeader.className = 'code-card-header';
            cardHeader.innerHTML = '<span>' + lang.toUpperCase() + '</span><button class="btn-copy">📋 Copy</button>';
            card.appendChild(cardHeader);

            const copyBtn = cardHeader.querySelector('.btn-copy');
            copyBtn.onclick = () => {
              navigator.clipboard.writeText(code);
              copyBtn.textContent = '✓ Copied';
              setTimeout(() => { copyBtn.textContent = '📋 Copy'; }, 2000);
            };

            const content = document.createElement('div');
            content.className = 'code-content';
            content.textContent = code;
            card.appendChild(content);

            const footer = document.createElement('div');
            footer.className = 'code-footer';

            const applyBtn = document.createElement('button');
            applyBtn.className = 'card-btn btn-apply';
            applyBtn.textContent = '⚡ Apply / Replace';
            applyBtn.onclick = () => {
              vscode.postMessage({ type: 'applyCode', code: part, mode: 'smart' });
            };

            const caretBtn = document.createElement('button');
            caretBtn.className = 'card-btn btn-caret';
            caretBtn.textContent = '➕ Caret';
            caretBtn.onclick = () => {
              vscode.postMessage({ type: 'applyCode', code: part, mode: 'caret' });
            };

            footer.appendChild(applyBtn);
            footer.appendChild(caretBtn);
            card.appendChild(footer);
            msgDiv.appendChild(card);
          } else if (part.trim().length > 0) {
            const bubble = document.createElement('div');
            bubble.className = 'bubble';
            bubble.textContent = part.trim();
            msgDiv.appendChild(bubble);
          }
        });
      } else {
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = text;
        msgDiv.appendChild(bubble);
      }

      chatHistory.appendChild(msgDiv);
      scrollToBottom();
    }

    function send() {
      const text = promptInput.value.trim();
      if (!text) return;
      promptInput.value = '';
      vscode.postMessage({ type: 'sendMessage', text });
    }

    sendBtn.onclick = send;
    promptInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send();
      }
    });

    clearBtn.onclick = () => {
      chatHistory.innerHTML = '';
      appendMessage('Chat cleared. Ready for your next mission.', false);
    };

    settingsBtn.onclick = () => {
      vscode.postMessage({ type: 'openSettings' });
    };

    modelSelector.onchange = () => {
      vscode.postMessage({ type: 'changeModel', model: modelSelector.value });
    };

    document.querySelectorAll('.action-pill').forEach(pill => {
      pill.onclick = () => {
        const action = pill.getAttribute('data-action');
        vscode.postMessage({ type: 'quickAction', action });
      };
    });

    let spinnerElem = null;

    window.addEventListener('message', event => {
      const msg = event.data;
      if (msg.type === 'addMessage') {
        appendMessage(msg.message.text, msg.message.isUser);
      } else if (msg.type === 'setLoading') {
        if (msg.loading) {
          if (!spinnerElem) {
            spinnerElem = document.createElement('div');
            spinnerElem.className = 'spinner';
            spinnerElem.innerHTML = '<div class="dot"></div><span>' + (msg.status || 'Thinking...') + '</span>';
            chatHistory.appendChild(spinnerElem);
            scrollToBottom();
          }
        } else {
          if (spinnerElem) {
            spinnerElem.remove();
            spinnerElem = null;
          }
        }
      }
    });
  </script>
</body>
</html>`;
  }
}
