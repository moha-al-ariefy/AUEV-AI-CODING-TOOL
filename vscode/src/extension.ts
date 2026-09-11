import * as vscode from "vscode";
import { AuevAiClient } from "./aiClient";
import { AuevInlineCompletionProvider } from "./ghostProvider";
import { AuditManager } from "./auditManager";
import { AuevChatViewProvider } from "./chatViewProvider";

let statusBarItem: vscode.StatusBarItem;

export function activate(context: vscode.ExtensionContext) {
  console.log("👻 AUEV (AI Unified Editor Vision) extension is now active in VS Code!");

  // 1. Register Webview Chat View Provider (Sidebar)
  const chatProvider = new AuevChatViewProvider(context.extensionUri);
  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(
      AuevChatViewProvider.viewType,
      chatProvider
    )
  );

  // 2. Register Inline Ghost Text Provider
  const inlineProvider = new AuevInlineCompletionProvider();
  context.subscriptions.push(
    vscode.languages.registerInlineCompletionItemProvider(
      { pattern: "**" },
      inlineProvider
    )
  );

  // 3. Register Commands
  context.subscriptions.push(
    vscode.commands.registerCommand("auev.openChat", () => {
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.auditFile", async () => {
      await AuditManager.auditActiveFile();
    }),

    vscode.commands.registerCommand("auev.threatModel", async () => {
      await chatProvider.postExternalAction("threatModel");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.supplyChainAudit", async () => {
      await chatProvider.postExternalAction("supplyChain");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.inputShield", async () => {
      await chatProvider.postExternalAction("inputShield");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.addModel", async () => {
      const model = await vscode.window.showInputBox({
        prompt: "Enter custom model name (e.g., deepseek/deepseek-r1, qwen/qwen-2.5-coder-32b, ollama/codellama)",
        placeHolder: "provider/model-id"
      });
      if (model && model.trim()) {
        await AuevAiClient.addCustomModel(model.trim());
        vscode.window.showInformationMessage(`AUEV: Custom model '${model.trim()}' added.`);
      }
    }),

    vscode.commands.registerCommand("auev.explainCode", async () => {
      await chatProvider.postExternalAction("explain");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.refactorCode", async () => {
      await chatProvider.postExternalAction("refactor");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.generateTests", async () => {
      await chatProvider.postExternalAction("tests");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.sanitizeCode", async () => {
      await chatProvider.postExternalAction("sanitize");
      vscode.commands.executeCommand("auev.chatView.focus");
    }),

    vscode.commands.registerCommand("auev.toggleGhost", async () => {
      const config = vscode.workspace.getConfiguration("auev");
      const current = config.get<boolean>("enableGhostText", true);
      await config.update("enableGhostText", !current, vscode.ConfigurationTarget.Global);
      vscode.window.showInformationMessage(
        `AUEV: Ghost Text is now ${!current ? "ENABLED 👻" : "DISABLED 💤"}`
      );
      updateStatusBar();
    }),

    vscode.commands.registerCommand("auev.toggleParanoid", async () => {
      const config = vscode.workspace.getConfiguration("auev");
      const current = config.get<boolean>("paranoidMode", true);
      await config.update("paranoidMode", !current, vscode.ConfigurationTarget.Global);
      vscode.window.showInformationMessage(
        `AUEV: Paranoid Mode is now ${!current ? "ACTIVATED 🛡️ (Strict OWASP)" : "DEACTIVATED ⚡"}`
      );
      updateStatusBar();
    })
  );

  // 4. Create Status Bar Item
  statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
  statusBarItem.command = "auev.openChat";
  context.subscriptions.push(statusBarItem);

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration("auev")) {
        updateStatusBar();
      }
    })
  );

  updateStatusBar();
  statusBarItem.show();
}

function updateStatusBar() {
  const isGhost = AuevAiClient.isGhostEnabled();
  const isParanoid = AuevAiClient.isParanoidMode();

  if (isParanoid) {
    statusBarItem.text = `$(shield) AUEV: Paranoid`;
    statusBarItem.tooltip = `AUEV: Paranoid Mode Active (OWASP Top 10 + Ghost: ${isGhost ? "ON" : "OFF"})\nClick to open assistant`;
    statusBarItem.backgroundColor = undefined;
  } else {
    statusBarItem.text = `$(zap) AUEV: ${isGhost ? "Ghost ON" : "Ready"}`;
    statusBarItem.tooltip = `AUEV: Standard Mode (Ghost: ${isGhost ? "ON" : "OFF"})\nClick to open assistant`;
  }
}

export function deactivate() {
  if (statusBarItem) {
    statusBarItem.dispose();
  }
}
