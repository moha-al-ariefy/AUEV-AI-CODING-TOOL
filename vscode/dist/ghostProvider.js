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
exports.AuevInlineCompletionProvider = void 0;
const vscode = __importStar(require("vscode"));
const aiClient_1 = require("./aiClient");
const ghostSanitizer_1 = require("./ghostSanitizer");
class AuevInlineCompletionProvider {
    debounceTimer = null;
    abortController = null;
    async provideInlineCompletionItems(document, position, context, token) {
        if (!aiClient_1.AuevAiClient.isGhostEnabled()) {
            return undefined;
        }
        // Cancel any ongoing in-flight request
        if (this.abortController) {
            this.abortController.abort();
            this.abortController = null;
        }
        // Debounce typing (350ms)
        await new Promise((resolve) => {
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
            const rawCompletion = await aiClient_1.AuevAiClient.completeGhost(prefix, suffix, this.abortController.signal);
            if (token.isCancellationRequested || !rawCompletion || rawCompletion.trim().length === 0) {
                return undefined;
            }
            // Security Tripwire Check: Snipe hallucinated or leaked secrets
            if (ghostSanitizer_1.GhostSanitizer.containsSecrets(rawCompletion)) {
                console.warn("[AUEV] Tripwire tripped: completion contained high-entropy secrets. Suppressed.");
                return undefined;
            }
            const result = ghostSanitizer_1.GhostSanitizer.sanitize(currentLinePrefix, suffix, rawCompletion);
            if (!result.textToInsert || result.textToInsert.trim().length === 0) {
                return undefined;
            }
            let replaceRange;
            if (result.charsToDelete > 0) {
                const deleteStartPos = position.translate(0, -result.charsToDelete);
                replaceRange = new vscode.Range(deleteStartPos, position);
            }
            else {
                replaceRange = new vscode.Range(position, position);
            }
            const item = new vscode.InlineCompletionItem(result.textToInsert, replaceRange);
            return [item];
        }
        catch {
            return undefined;
        }
    }
}
exports.AuevInlineCompletionProvider = AuevInlineCompletionProvider;
//# sourceMappingURL=ghostProvider.js.map