"use strict";
/**
 * AUEV - Dual-Agent Ghost Sanitizer & Local Heuristic Shield
 * Ported and enhanced from AUEV Kotlin engine.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.GhostSanitizer = void 0;
class GhostSanitizer {
    static SECRET_PATTERNS = [
        /AKIA[0-9A-Z]{16}/, // AWS Access Key
        /sk-[a-zA-Z0-9]{48,}/, // OpenAI API Key
        /sk-proj-[a-zA-Z0-9\-_]{48,}/, // OpenAI Project Key
        /sk-ant-[a-zA-Z0-9\-_]{32,}/, // Anthropic Key
        /gsk_[a-zA-Z0-9]{48,}/, // Groq Key
        /ghp_[a-zA-Z0-9]{36}/, // GitHub Classic PAT
        /github_pat_[a-zA-Z0-9_]{82}/, // GitHub Fine-grained PAT
        /-----BEGIN [A-Z ]+PRIVATE KEY-----/, // RSA / EC / SSH private key
        /ey[A-Za-z0-9-_]{10,}\.ey[A-Za-z0-9-_]{10,}\.[A-Za-z0-9-_]{10,}/, // JWT Token
        /(postgres|mysql|mongodb|redis):\/\/[^:]+:[^@]+@/i, // DB Connection URI
        /(password|secret|token|api[_-]?key)["']?\s*[:=]\s*["'][a-zA-Z0-9_\-@!#$%^&*]{8,}["']/i
    ];
    /**
     * Check whether raw completion hallucinates or leaks credentials
     */
    static containsSecrets(text) {
        return this.SECRET_PATTERNS.some((pattern) => pattern.test(text));
    }
    /**
     * Clean markdown, resolve prefix/suffix overlap, and sanitize security hazards
     */
    static sanitize(userPrefix, userSuffix, aiSuggestion) {
        // 1. Clean markdown code fences & literal escape artifacts
        let cleanAI = aiSuggestion
            .replace(/^```[a-zA-Z0-9_-]*/, "")
            .replace(/```$/, "")
            .replace(/\\n/g, "\n")
            .replace(/\\t/g, "\t");
        let charsToDelete = 0;
        // 2. Prefix Overlap Handling
        const firstNonWhitespace = cleanAI.trim().charAt(0);
        const aiStartsSymbol = firstNonWhitespace !== "" && !/[a-zA-Z0-9]/.test(firstNonWhitespace);
        if (aiStartsSymbol) {
            if (userPrefix.length > 0 && cleanAI.startsWith(userPrefix)) {
                cleanAI = cleanAI.substring(userPrefix.length);
            }
        }
        else {
            if (userPrefix.length > 0 && cleanAI.startsWith(userPrefix)) {
                cleanAI = cleanAI.substring(userPrefix.length);
            }
            else if (userPrefix.length > 0 && cleanAI.toLowerCase().startsWith(userPrefix.toLowerCase())) {
                charsToDelete = userPrefix.length;
            }
            else if (userPrefix.length > 0 &&
                !userPrefix.includes(" ") &&
                !userPrefix.includes("\n") &&
                !userPrefix.includes(".") &&
                /^[a-zA-Z0-9]+$/.test(userPrefix)) {
                // Handle trigger expansions (e.g., 'sysout' -> 'System.out.println')
                const firstCharAI = cleanAI.trim().charAt(0).toLowerCase();
                const firstCharUser = userPrefix.trim().charAt(0).toLowerCase();
                const startsDifferent = firstCharAI !== firstCharUser;
                let tailMatch = false;
                if (userPrefix.length > 2) {
                    const checkLen = Math.min(userPrefix.length, 15);
                    for (let i = checkLen; i >= 3; i--) {
                        if (cleanAI.includes(userPrefix.slice(-i))) {
                            tailMatch = true;
                            break;
                        }
                    }
                }
                if (startsDifferent || tailMatch) {
                    charsToDelete = userPrefix.length;
                }
            }
        }
        // 3. Aggressive Zipper (Prevents repeating existing suffix)
        if (userSuffix.trim().length > 0 && cleanAI.trim().length > 0) {
            const normalizedSuffix = userSuffix.trimStart();
            for (let i = 0; i < cleanAI.length; i++) {
                const aiChunk = cleanAI.substring(i);
                if (normalizedSuffix.startsWith(aiChunk.trimStart())) {
                    const isStructural = aiChunk.trim() === "}" || aiChunk.trim() === ");" || aiChunk.trim() === ";";
                    if (aiChunk.length > 3 || isStructural) {
                        cleanAI = cleanAI.substring(0, i);
                        break;
                    }
                }
            }
        }
        // 4. Local Heuristic Scrubber & Automated Upgrades
        // 4A. Redact secrets
        cleanAI = cleanAI
            .replace(/AKIA[0-9A-Z]{16}/g, "\"[REDACTED_AWS_KEY]\"")
            .replace(/ghp_[a-zA-Z0-9]{36}/g, "\"[REDACTED_GITHUB_PAT]\"")
            .replace(/github_pat_[a-zA-Z0-9_]{82}/g, "\"[REDACTED_GITHUB_FINE_GRAINED_PAT]\"")
            .replace(/sk-[a-zA-Z0-9]{48,}/g, "\"[REDACTED_OPENAI_KEY]\"")
            .replace(/sk-ant-[a-zA-Z0-9\-_]{32,}/g, "\"[REDACTED_ANTHROPIC_KEY]\"")
            .replace(/gsk_[a-zA-Z0-9]{48,}/g, "\"[REDACTED_GROQ_KEY]\"")
            .replace(/-----BEGIN [A-Z ]+PRIVATE KEY-----[\s\S]*?-----END [A-Z ]+PRIVATE KEY-----/g, "\"[REDACTED_PRIVATE_KEY]\"")
            .replace(/(password|secret|token|api_?key|pwd|client_secret)\s*(:|=)\s*["'][^"'\s]{6,}["']/gi, "$1 $2 \"[REDACTED BY AUEV]\"");
        // 4B. Upgrade weak cryptographic primitives
        cleanAI = cleanAI
            .replace(/getInstance\(["']MD5["']\)/gi, "getInstance(\"SHA-256\" /* AUEV: Upgraded from weak MD5 */)")
            .replace(/getInstance\(["']SHA-1["']\)/gi, "getInstance(\"SHA-256\" /* AUEV: Upgraded from weak SHA-1 */)")
            .replace(/Cipher\.getInstance\(["']DES["']\)/gi, "Cipher.getInstance(\"AES/GCM/NoPadding\" /* AUEV: Upgraded from insecure DES */)")
            .replace(/hashlib\.md5\(/g, "hashlib.sha256( # AUEV: Upgraded from weak MD5\n")
            .replace(/hashlib\.sha1\(/g, "hashlib.sha256( # AUEV: Upgraded from weak SHA-1\n")
            .replace(/crypto\.createHash\(["']md5["']\)/gi, "crypto.createHash(\"sha256\") /* AUEV: Upgraded from weak MD5 */")
            .replace(/crypto\.createHash\(["']sha1["']\)/gi, "crypto.createHash(\"sha256\") /* AUEV: Upgraded from weak SHA-1 */");
        return {
            textToInsert: cleanAI.trimEnd(),
            charsToDelete
        };
    }
}
exports.GhostSanitizer = GhostSanitizer;
//# sourceMappingURL=ghostSanitizer.js.map