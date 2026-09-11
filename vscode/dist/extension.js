"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const aiClient_1 = require("./aiClient");
const ghostProvider_1 = require("./ghostProvider");
const auditManager_1 = require("./auditManager");
const chatViewProvider_1 = require("./chatViewProvider");
let statusBarItem;
function activate(context) {
    console.log("👻 AUEV (AI Unified Editor Vision) extension is now active in VS Code!");
    // 1. Register Webview Chat View Provider (Sidebar)
    const chatProvider = new chatViewProvider_1.AuevChatViewProvider(context.extensionUri);
    context.subscriptions.push(vscode.window.registerWebviewViewProvider(chatViewProvider_1.AuevChatViewProvider.viewType, chatProvider));
    // 2. Register Inline Ghost Text Provider
    const inlineProvider = new ghostProvider_1.AuevInlineCompletionProvider();
    context.subscriptions.push(vscode.languages.registerInlineCompletionItemProvider({ pattern: "**" }, inlineProvider));
    // 3. Register Commands
    context.subscriptions.push(vscode.commands.registerCommand("auev.openChat", () => {
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.openApiKeyGuide", async () => {
        await chatProvider.postExternalAction("apiKeyGuide");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.auditFile", async () => {
        await auditManager_1.AuditManager.auditActiveFile();
    }), vscode.commands.registerCommand("auev.threatModel", async () => {
        await chatProvider.postExternalAction("threatModel");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.supplyChainAudit", async () => {
        await chatProvider.postExternalAction("supplyChain");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.inputShield", async () => {
        await chatProvider.postExternalAction("inputShield");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.addModel", async () => {
        const model = await vscode.window.showInputBox({
            prompt: "Enter custom model name (e.g., deepseek/deepseek-r1, qwen/qwen-2.5-coder-32b, ollama/codellama)",
            placeHolder: "provider/model-id"
        });
        if (model && model.trim()) {
            await aiClient_1.AuevAiClient.addCustomModel(model.trim());
            vscode.window.showInformationMessage(`AUEV: Custom model '${model.trim()}' added.`);
        }
    }), vscode.commands.registerCommand("auev.explainCode", async () => {
        await chatProvider.postExternalAction("explain");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.refactorCode", async () => {
        await chatProvider.postExternalAction("refactor");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.generateTests", async () => {
        await chatProvider.postExternalAction("tests");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.sanitizeCode", async () => {
        await chatProvider.postExternalAction("sanitize");
        vscode.commands.executeCommand("auev.chatView.focus");
    }), vscode.commands.registerCommand("auev.toggleGhost", async () => {
        const config = vscode.workspace.getConfiguration("auev");
        const current = config.get("enableGhostText", true);
        await config.update("enableGhostText", !current, vscode.ConfigurationTarget.Global);
        vscode.window.showInformationMessage(`AUEV: Ghost Text is now ${!current ? "ENABLED 👻" : "DISABLED 💤"}`);
        updateStatusBar();
    }), vscode.commands.registerCommand("auev.toggleParanoid", async () => {
        const config = vscode.workspace.getConfiguration("auev");
        const current = config.get("paranoidMode", true);
        await config.update("paranoidMode", !current, vscode.ConfigurationTarget.Global);
        vscode.window.showInformationMessage(`AUEV: Paranoid Mode is now ${!current ? "ACTIVATED 🛡️ (Strict OWASP)" : "DEACTIVATED ⚡"}`);
        updateStatusBar();
    }));
    // 4. Create Status Bar Item
    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    statusBarItem.command = "auev.openChat";
    context.subscriptions.push(statusBarItem);
    context.subscriptions.push(vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration("auev")) {
            updateStatusBar();
        }
    }));
    updateStatusBar();
    statusBarItem.show();
}
function updateStatusBar() {
    const isGhost = aiClient_1.AuevAiClient.isGhostEnabled();
    const isParanoid = aiClient_1.AuevAiClient.isParanoidMode();
    if (isParanoid) {
        statusBarItem.text = `$(shield) AUEV: Paranoid`;
        statusBarItem.tooltip = `AUEV: Paranoid Mode Active (OWASP Top 10 + Ghost: ${isGhost ? "ON" : "OFF"})\nClick to open assistant`;
        statusBarItem.backgroundColor = undefined;
    }
    else {
        statusBarItem.text = `$(zap) AUEV: ${isGhost ? "Ghost ON" : "Ready"}`;
        statusBarItem.tooltip = `AUEV: Standard Mode (Ghost: ${isGhost ? "ON" : "OFF"})\nClick to open assistant`;
    }
}
function deactivate() {
    if (statusBarItem) {
        statusBarItem.dispose();
    }
}
//# sourceMappingURL=extension.js.map