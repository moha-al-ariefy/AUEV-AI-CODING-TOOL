/*
 * Copyright 2026 moha-al-ariefy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hackathon.aihelper

object GhostSanitizer {

    data class MergeResult(
        val textToInsert: String,
        val charsToDelete: Int
    )

    fun sanitize(userPrefix: String, userSuffix: String, aiSuggestion: String): MergeResult {
        // 1. Clean Markdown & Literal Escapes
        var cleanAI = aiSuggestion
            .replace(Regex("^```[a-zA-Z]*"), "")
            .replace(Regex("```$"), "")
            .replace("\\n", "\n")
            .replace("\\t", "\t")

        var charsToDelete = 0

        // ---------------------------------------------------------
        // 2. PREFIX HANDLING
        // ---------------------------------------------------------

        // Guard: If AI starts with a symbol (e.g. ( . { [ ), it's an APPEND.
        // We never delete the user's text if the AI is just adding arguments.
        val aiStartsSymbol = cleanAI.trim().firstOrNull()?.let { !it.isLetterOrDigit() } ?: false

        if (aiStartsSymbol) {
            // Case A (Special): Check for exact overlapping symbols (e.g. user typed "(" and AI gave "(")
            if (userPrefix.isNotEmpty() && cleanAI.startsWith(userPrefix)) {
                cleanAI = cleanAI.substring(userPrefix.length)
            }
            // Otherwise, Keep charsToDelete = 0
        }
        else {
            // Case B: Exact Overlap (User: "Sys", AI: "System")
            if (userPrefix.isNotEmpty() && cleanAI.startsWith(userPrefix)) {
                cleanAI = cleanAI.substring(userPrefix.length)
            }
            // Case C: Case-Insensitive Overlap (User: "sys", AI: "System")
            else if (userPrefix.isNotEmpty() && cleanAI.startsWith(userPrefix, ignoreCase = true)) {
                charsToDelete = userPrefix.length
            }
            // Case D: Typo/Trigger (User: "sysout", AI: "System...")
            // CRITICAL FIX: Only attempt replacement if userPrefix is a SIMPLE word (no dots, no symbols)
            else if (userPrefix.isNotEmpty()
                && !userPrefix.contains(" ")
                && !userPrefix.contains("\n")
                && !userPrefix.contains(".") // <--- SAVES 'System.out.println'
                && userPrefix.all { it.isLetterOrDigit() }) {

                val firstCharAI = cleanAI.trim().firstOrNull()?.toString() ?: ""
                val firstCharUser = userPrefix.trim().firstOrNull()?.toString() ?: ""

                val startsDifferent = !firstCharAI.equals(firstCharUser, ignoreCase = true)
                var tailMatch = false

                if (userPrefix.length > 2) {
                    val checkLen = Math.min(userPrefix.length, 15)
                    for (i in checkLen downTo 3) {
                        if (cleanAI.contains(userPrefix.takeLast(i))) {
                            tailMatch = true
                            break
                        }
                    }
                }

                if (startsDifferent || tailMatch) {
                    charsToDelete = userPrefix.length
                }
            }
        }

        // ---------------------------------------------------------
        // 3. AGGRESSIVE ZIPPER (The Repetition Killer)
        // ---------------------------------------------------------
        if (userSuffix.isNotBlank() && cleanAI.isNotBlank()) {
            val normalizedSuffix = userSuffix.trimStart()

            for (i in 0 until cleanAI.length) {
                val aiChunk = cleanAI.substring(i)

                // If suffix starts with this chunk...
                if (normalizedSuffix.startsWith(aiChunk.trimStart())) {
                    // Only zipper if it's a structural match or >3 chars
                    val isStructural = aiChunk.trim() == "}" || aiChunk.trim() == ");" || aiChunk.trim() == ";"
                    if (aiChunk.length > 3 || isStructural) {
                        cleanAI = cleanAI.substring(0, i)
                        break
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 4. THE SECURITY SCRUBBER (The "Auto-Sanitization" Feature)
        // ---------------------------------------------------------
        // Aggressive local heuristics intercept weak crypto and secrets across languages

        // 4A. Redact high-entropy secret patterns
        cleanAI = cleanAI
            .replace(Regex("AKIA[0-9A-Z]{16}"), "\"[REDACTED_AWS_KEY]\"")
            .replace(Regex("ghp_[a-zA-Z0-9]{36}"), "\"[REDACTED_GITHUB_PAT]\"")
            .replace(Regex("github_pat_[a-zA-Z0-9_]{82}"), "\"[REDACTED_GITHUB_FINE_GRAINED_PAT]\"")
            .replace(Regex("sk-[a-zA-Z0-9]{48}"), "\"[REDACTED_OPENAI_KEY]\"")
            .replace(Regex("sk-ant-[a-zA-Z0-9\\-_]{32,}"), "\"[REDACTED_ANTHROPIC_KEY]\"")
            .replace(Regex("gsk_[a-zA-Z0-9]{48,}"), "\"[REDACTED_GROQ_KEY]\"")
            .replace(Regex("-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"), "\"[REDACTED_PRIVATE_KEY]\"")
            .replace(Regex("ey[A-Za-z0-9-_]{10,}\\.ey[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,}"), "\"[REDACTED_JWT]\"")

        // 4B. Scrub hardcoded credentials (Variables named password, secret, token, key)
        cleanAI = cleanAI.replace(
            Regex("(?i)(password|secret|token|api_?key|pwd|client_secret)\\s*(:|=)\\s*[\"'][^\"'\\s]{6,}[\"']"),
            "$1 $2 \"[REDACTED BY AUEV]\""
        )

        // 4C. Upgrade weak crypto algorithms (Java/Kotlin, Python, JavaScript/TypeScript)
        cleanAI = cleanAI
            .replace(
                Regex("(?i)getInstance\\([\"']MD5[\"']\\)"),
                "getInstance(\"SHA-256\" /* AUEV: Upgraded from weak MD5 */)"
            )
            .replace(
                Regex("(?i)getInstance\\([\"']SHA-1[\"']\\)"),
                "getInstance(\"SHA-256\" /* AUEV: Upgraded from weak SHA-1 */)"
            )
            .replace(
                Regex("(?i)Cipher\\.getInstance\\([\"']DES[\"']\\)"),
                "Cipher.getInstance(\"AES/GCM/NoPadding\" /* AUEV: Upgraded from insecure DES */)"
            )
            .replace(
                Regex("hashlib\\.md5\\("),
                "hashlib.sha256( # AUEV: Upgraded from weak MD5\n"
            )
            .replace(
                Regex("hashlib\\.sha1\\("),
                "hashlib.sha256( # AUEV: Upgraded from weak SHA-1\n"
            )
            .replace(
                Regex("crypto\\.createHash\\([\"']md5[\"']\\)"),
                "crypto.createHash(\"sha256\") /* AUEV: Upgraded from weak MD5 */"
            )
            .replace(
                Regex("crypto\\.createHash\\([\"']sha1[\"']\\)"),
                "crypto.createHash(\"sha256\") /* AUEV: Upgraded from weak SHA-1 */"
            )

        return MergeResult(cleanAI.trimEnd(), charsToDelete)
    }
}