import * as vscode from "vscode";
import { AuevAiClient } from "./aiClient";
import { GhostSanitizer } from "./ghostSanitizer";

export class AuevInlineCompletionProvider implements vscode.InlineCompletionItemProvider {
  private debounceTimer: NodeJS.Timeout | null = null;
  private abortController: AbortController | null = null;

  public async provideInlineCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    context: vscode.InlineCompletionContext,
    token: vscode.CancellationToken
  ): Promise<vscode.InlineCompletionItem[] | undefined> {
    if (!AuevAiClient.isGhostEnabled()) {
      return undefined;
    }

    // Cancel any ongoing in-flight request
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }

    // Debounce typing (350ms)
    await new Promise<void>((resolve) => {
      if (this.debounceTimer) {
        clearTimeout(this.debounceTimer);
      }
      this.debounceTimer = setTimeout(() => resolve(), 350);
    });

    if (token.isCancellationRequested) {
      return undefined;
    }

    const currentOffset = document.offsetAt(position);
    const text = document.getText();

    const startOffset = Math.max(0, currentOffset - 2500);
    const prefix = text.slice(startOffset, currentOffset);

    const endOffset = Math.min(text.length, currentOffset + 1000);
    const suffix = text.slice(currentOffset, endOffset);

    const line = document.lineAt(position.line);
    const currentLinePrefix = line.text.slice(0, position.character).trim();

    if (!prefix.trim()) {
      return undefined;
    }

    this.abortController = new AbortController();

    try {
      const rawCompletion = await AuevAiClient.completeGhost(
        prefix,
        suffix,
        this.abortController.signal
      );

      if (token.isCancellationRequested || !rawCompletion || rawCompletion.trim().length === 0) {
        return undefined;
      }

      // Security Tripwire Check: Snipe hallucinated or leaked secrets
      if (GhostSanitizer.containsSecrets(rawCompletion)) {
        console.warn("[AUEV] Tripwire tripped: completion contained high-entropy secrets. Suppressed.");
        return undefined;
      }

      const result = GhostSanitizer.sanitize(currentLinePrefix, suffix, rawCompletion);

      if (!result.textToInsert || result.textToInsert.trim().length === 0) {
        return undefined;
      }

      let replaceRange: vscode.Range;
      if (result.charsToDelete > 0) {
        const deleteStartPos = position.translate(0, -result.charsToDelete);
        replaceRange = new vscode.Range(deleteStartPos, position);
      } else {
        replaceRange = new vscode.Range(position, position);
      }

      const item = new vscode.InlineCompletionItem(result.textToInsert, replaceRange);
      return [item];
    } catch {
      return undefined;
    }
  }
}
