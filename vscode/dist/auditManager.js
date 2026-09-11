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
exports.AuditManager = void 0;
const vscode = __importStar(require("vscode"));
const aiClient_1 = require("./aiClient");
class AuditManager {
    static async auditActiveFile(onResult) {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showWarningMessage("AUEV: No active editor file to audit.");
            return undefined;
        }
        const document = editor.document;
        const selection = editor.selection;
        const text = (!selection.isEmpty ? document.getText(selection) : document.getText()).trim();
        if (!text) {
            vscode.window.showWarningMessage("AUEV: The active file or selection is empty.");
            return undefined;
        }
        const fileName = document.fileName.split(/[/\\]/).pop() || "Untitled";
        const languageId = document.languageId;
        return vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: `AUEV: Running Security Audit on ${fileName}...`,
            cancellable: false
        }, async () => {
            try {
                const auditReport = await aiClient_1.AuevAiClient.runAudit(fileName, languageId, text);
                if (onResult) {
                    onResult(auditReport);
                }
                // Also open in markdown preview / side editor if triggered as a direct command
                const doc = await vscode.workspace.openTextDocument({
                    content: auditReport,
                    language: "markdown"
                });
                await vscode.window.showTextDocument(doc, {
                    viewColumn: vscode.ViewColumn.Beside,
                    preserveFocus: true
                });
                vscode.window.showInformationMessage("🛡️ AUEV: Security audit completed.");
                return auditReport;
            }
            catch (err) {
                vscode.window.showErrorMessage(`AUEV: Audit failed - ${err.message || err}`);
                return undefined;
            }
        });
    }
}
exports.AuditManager = AuditManager;
//# sourceMappingURL=auditManager.js.map