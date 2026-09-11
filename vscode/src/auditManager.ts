import * as vscode from "vscode";
import { AuevAiClient } from "./aiClient";

export class AuditManager {
  public static async auditActiveFile(onResult?: (result: string) => void): Promise<string | undefined> {
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

    return vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `AUEV: Running Security Audit on ${fileName}...`,
        cancellable: false
      },
      async () => {
        try {
          const auditReport = await AuevAiClient.runAudit(fileName, languageId, text);
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
        } catch (err: any) {
          vscode.window.showErrorMessage(`AUEV: Audit failed - ${err.message || err}`);
          return undefined;
        }
      }
    );
  }
}
