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

package com.hackathon.aihelper.ui

import com.hackathon.aihelper.settings.AppSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI // <--- Java 21 Friend
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

object ChatService {

    private val apiKey: String
        get() = AppSettingsState.getInstance().apiKey.trim()

    private val model: String
        get() = AppSettingsState.getInstance().modelName.trim()

    private val isParanoidMode: Boolean
        get() = AppSettingsState.getInstance().paranoidMode

    private val customApiUrl: String
        get() = AppSettingsState.getInstance().customApiUrl.trim()

    private enum class Provider { OPENAI, ANTHROPIC, GROQ, CUSTOM }

    private fun getProvider(): Provider {
        if (customApiUrl.isNotBlank()) return Provider.CUSTOM
        return when {
            apiKey.startsWith("sk-ant-") -> Provider.ANTHROPIC
            apiKey.startsWith("gsk_") -> Provider.GROQ
            else -> Provider.OPENAI
        }
    }

    // --- CHAT LOGIC ---
    fun sendMessage(project: Project, userPrompt: String, onResponse: Consumer<String>) {
        if (apiKey.isBlank() && customApiUrl.isBlank()) {
            onResponse.accept("⚠️ Please configure your API Key in Settings (⚙).")
            return
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val currentCode = editor?.document?.text ?: ""
        val selectedCode = editor?.selectionModel?.selectedText ?: ""
        val fileExtension = editor?.virtualFile?.extension ?: "txt"
        val fileName = editor?.virtualFile?.name ?: "Unknown"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                var systemPrompt = """
                    You are AUEV (AI Unified Editor Vision), an elite security-first pair programmer integrated into IntelliJ IDEA.
                    
                    MISSION:
                    - Deliver clean, robust, highly maintainable, production-ready code.
                    - Be direct, concise, and technically accurate.
                    
                    RULES FOR CODE:
                    - ALWAYS output complete, working code blocks enclosed in ```language ... ``` markdown.
                    - Follow language idiomatic best practices (SOLID, modern standard libraries).
                    - Professional documentation only (Javadoc/KDoc/docstrings where helpful). No conversational filler in code.
                    
                    CONTEXT:
                    - Active File: $fileName (.$fileExtension)
                """.trimIndent()

                if (isParanoidMode) {
                    systemPrompt += """
                        
                        
                        🚨 STRICT SECURITY OVERRIDE (PARANOID MODE ACTIVE):
                        - OWASP Top 10 strict compliance: Block SQL Injection, Command Injection, XSS, SSRF, Broken Auth.
                        - ZERO HARDCODED CREDENTIALS: Never suggest code containing hardcoded tokens, passwords, API keys, or private keys.
                        - NEVER use weak cryptography (reject MD5, SHA-1, DES, ECB mode AES). Mandate SHA-256+, Argon2, bcrypt, AES-GCM.
                        - Enforce parameterized queries, safe deserialization, and strict input validation.
                        - If a requested pattern is unsafe, REFUSE, explain the CVE/OWASP risk, and provide the hardened alternative.
                    """.trimIndent()
                }

                val fullMessage = buildString {
                    if (selectedCode.isNotBlank()) {
                        append("Selected Code snippet:\n```$fileExtension\n$selectedCode\n```\n\n")
                    }
                    if (currentCode.isNotBlank()) {
                        val truncatedContext = if (currentCode.length > 8000) currentCode.take(8000) + "\n// ... [truncated for brevity]" else currentCode
                        append("File Context ($fileName):\n```$fileExtension\n$truncatedContext\n```\n\n")
                    }
                    append("User Request: $userPrompt")
                }

                val response = callAI(systemPrompt, fullMessage)

                ApplicationManager.getApplication().invokeLater {
                    onResponse.accept(response)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    onResponse.accept("Error: ${e.message}")
                }
            }
        }
    }

    // --- QUICK ACTIONS ---

    fun runAudit(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Security Scorecard & Audit",
            systemPrompt = """
                You are a Principal Security Auditor & OWASP Fellow.
                Audit the provided code for security vulnerabilities, OWASP Top 10 risks, secret leaks, and insecure dependencies.
                Structure your response with:
                1. 📊 Security Scorecard:
                   - Security Grade: [A+ / A / B / C / D / F]
                   - Risk Level: [LOW / MEDIUM / HIGH / CRITICAL]
                   - Exploitability Index: [1-10]
                   - OWASP Categories Triggered: [e.g. A01:2021 Broken Access Control, A03:2021 Injection]
                2. 🚨 Vulnerability Analysis (line references, attack vectors, CVSS estimate)
                3. 🛡️ Secure Remediated Code (Full corrected code block ready for deployment)
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runThreatModel(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "STRIDE Threat Modeling",
            systemPrompt = """
                You are a Principal Security Architect & Threat Modeling Specialist.
                Perform a formal STRIDE Threat Modeling assessment on the provided code/module.
                Analyze the following threat categories:
                - [S] Spoofing (Identity spoofing, session hijacking, unauthenticated access)
                - [T] Tampering (Data corruption, in-flight alteration, parameter tampering)
                - [R] Repudiation (Lack of audit logging, deniability of critical actions)
                - [I] Information Disclosure (Data exposure, stack trace leakage, side channels)
                - [D] Denial of Service (Algorithmic complexity, unconstrained resource consumption)
                - [E] Elevation of Privilege (Role bypass, IDOR, path traversal, unsafe reflection)

                Format your output with:
                1. 🎯 STRIDE Threat Matrix (Table: Threat | Vector | Severity | Trust Boundary)
                2. 🛡️ Attack Surface & Trust Boundary Breakdown
                3. 🔒 Hardened Architecture & Mitigation Code (Complete runnable fix)
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runSupplyChainAudit(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Supply Chain & Dependency Audit",
            systemPrompt = """
                You are a DevSecOps & Software Supply Chain Security Architect.
                Analyze the provided code, imports, dependency declarations, and package references.
                Evaluate:
                1. 📦 Dependency & Manifest Risks (vulnerable packages, known CVEs, unpinned floating ranges)
                2. ⚠️ Typosquatting & Malicious Package Vectors (suspicious library names or risky postinstall hooks)
                3. 🔒 Repository Integrity (insecure HTTP endpoints, missing checksums/hashes)
                4. 📋 Supply Chain Scorecard & Pinned Safe Manifest Recommendation (Provide pinned manifest or safe import alternatives)
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runInputShield(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Input Validation & Contract Shield",
            systemPrompt = """
                You are an Application Security Engineer specializing in Defensive Design and Input Sanitization.
                Analyze the inputs, parameters, and contracts in the provided code.
                Produce:
                1. 🛡️ Input Boundary Analysis (identify all entry points: REST parameters, queries, body payloads, CLI args, file inputs)
                2. 🔒 Defensive Validation Shield (Generate strict schema validation / contract code using the most idiomatic library for the language: e.g. Zod/Joi for TS/JS, Pydantic for Python, Hibernate Validator/Bean Validation for Java/Kotlin, serde for Rust, etc.)
                3. 🧹 Sanitized Type Contracts & Enforced Boundary Assertions
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runExplain(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Code Explanation",
            systemPrompt = """
                You are a Staff Software Architect.
                Provide a clear, high-level and detailed architectural breakdown of the provided code.
                Explain:
                - What the code does and its primary flow
                - Key components, data structures, and algorithmic complexity
                - Potential edge cases or design tradeoffs
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runRefactor(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Refactoring",
            systemPrompt = """
                You are a Senior Refactoring Specialist.
                Refactor the provided code to maximize readability, performance, and clean code principles without breaking existing contracts.
                Provide:
                1. Brief bullet points of improvements made
                2. The complete refactored code block
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runGenerateTests(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Test Generation",
            systemPrompt = """
                You are a QA & Test Automation Architect.
                Generate comprehensive unit and edge-case tests (using standard frameworks like JUnit 5, Mockito, pytest, or Vitest depending on language).
                Cover happy path, boundaries, nullability, error handling, and security tripwires.
                Provide complete, runnable test classes/files.
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    fun runSanitize(project: Project, onResponse: Consumer<String>) {
        executeFileTask(
            project = project,
            taskName = "Security Sanitization",
            systemPrompt = """
                You are an Automated Security Sanitizer.
                Your job is to:
                1. Strip out or replace any hardcoded secrets/passwords with environment variable lookups.
                2. Upgrade deprecated/weak crypto (MD5, SHA-1 -> SHA-256).
                3. Convert raw SQL string concatenations to parameterized queries.
                Provide the clean, secure code block.
            """.trimIndent(),
            onResponse = onResponse
        )
    }

    private fun executeFileTask(
        project: Project,
        taskName: String,
        systemPrompt: String,
        onResponse: Consumer<String>
    ) {
        if (apiKey.isBlank() && customApiUrl.isBlank()) {
            onResponse.accept("⚠️ Please configure your API Key or Custom Endpoint in Settings.")
            return
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            onResponse.accept("⚠️ No file currently open in editor.")
            return
        }

        val selectedText = editor.selectionModel.selectedText
        val codeToAnalyze = if (!selectedText.isNullOrBlank()) selectedText else editor.document.text
        val fileExtension = editor.virtualFile?.extension ?: "txt"
        val fileName = editor.virtualFile?.name ?: "Unknown"

        if (codeToAnalyze.isBlank()) {
            onResponse.accept("⚠️ Open file or selection is empty.")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val userMsg = "File: $fileName (.$fileExtension)\n\n```$fileExtension\n$codeToAnalyze\n```"
                val response = callAI(systemPrompt, userMsg)
                ApplicationManager.getApplication().invokeLater {
                    onResponse.accept(response)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    onResponse.accept("⚠️ Error during $taskName: ${e.message}")
                }
            }
        }
    }

    // --- EDITOR MANIPULATION ---

    /**
     * Smart apply: if user selected text, replace the selection.
     * Otherwise insert at current caret position.
     */
    fun applyCodeToCurrentFile(project: Project, code: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val cleanCode = extractCodeBlock(code)

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project, "Apply AUEV Code", "AUEV", {
                val selectionModel = editor.selectionModel
                if (selectionModel.hasSelection()) {
                    val start = selectionModel.selectionStart
                    val end = selectionModel.selectionEnd
                    editor.document.replaceString(start, end, cleanCode)
                    editor.caretModel.moveToOffset(start + cleanCode.length)
                    selectionModel.removeSelection()
                } else {
                    val offset = editor.caretModel.offset
                    editor.document.insertString(offset, cleanCode)
                    editor.caretModel.moveToOffset(offset + cleanCode.length)
                }
            })
        }
    }

    fun replaceEntireFile(project: Project, code: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val cleanCode = extractCodeBlock(code)

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project, "Replace Entire File with AUEV Code", "AUEV", {
                editor.document.setText(cleanCode)
            })
        }
    }

    fun insertCodeAtCaret(project: Project, code: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val cleanCode = extractCodeBlock(code)

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project, "Insert AUEV Code at Caret", "AUEV", {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, cleanCode)
                editor.caretModel.moveToOffset(offset + cleanCode.length)
            })
        }
    }

    fun undoLastAction(project: Project) {
        val fileEditor = FileEditorManager.getInstance(project).selectedEditor
        if (fileEditor != null) {
            ApplicationManager.getApplication().invokeLater {
                val undoManager = UndoManager.getInstance(project)
                if (undoManager.isUndoAvailable(fileEditor)) {
                    undoManager.undo(fileEditor)
                }
            }
        }
    }

    // --- TEXT PARSING ---

    fun cleanMarkdown(text: String): String {
        return extractCodeBlock(text)
    }

    private fun extractCodeBlock(text: String): String {
        val pattern = Regex("```(?:[a-zA-Z0-9_-]*)?\\r?\\n([\\s\\S]*?)```")
        val match = pattern.find(text)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            if (text.startsWith("```") && text.endsWith("```")) {
                text.removeSurrounding("```").trim()
            } else {
                text.trim()
            }
        }
    }

    // --- NETWORK ENGINE ---

    private fun callAI(systemPrompt: String, userMessage: String): String {
        val provider = getProvider()

        val urlStr = if (customApiUrl.isNotBlank()) {
            customApiUrl
        } else {
            when (provider) {
                Provider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
                Provider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                Provider.OPENAI, Provider.CUSTOM -> "https://api.openai.com/v1/chat/completions"
            }
        }

        val actualModel = when {
            provider == Provider.GROQ && (model.isBlank() || model.startsWith("gpt")) -> "llama-3.3-70b-versatile"
            provider == Provider.ANTHROPIC && (model.isBlank() || model.startsWith("gpt")) -> "claude-3-5-sonnet-20240620"
            else -> model.ifBlank { "gpt-4o" }
        }

        val url = URI.create(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 45000

        if (provider == Provider.ANTHROPIC) {
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("content-type", "application/json")
        } else {
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.setRequestProperty("Content-Type", "application/json")
        }

        val jsonInput = if (provider == Provider.ANTHROPIC) {
            """
            {
                "model": "$actualModel",
                "max_tokens": 4096,
                "system": "${escapeJson(systemPrompt)}",
                "messages": [
                    {"role": "user", "content": "${escapeJson(userMessage)}"}
                ]
            }
            """.trimIndent()
        } else {
            """
            {
                "model": "$actualModel",
                "messages": [
                    {"role": "system", "content": "${escapeJson(systemPrompt)}"},
                    {"role": "user", "content": "${escapeJson(userMessage)}"}
                ],
                "max_tokens": 4096
            }
            """.trimIndent()
        }

        conn.outputStream.use { os -> os.write(jsonInput.toByteArray(StandardCharsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
            val parsedError = parseErrorMessage(errBody)
            throw RuntimeException("API Error ($responseCode): $parsedError")
        }

        val rawResponse = conn.inputStream.bufferedReader().use { it.readText() }
        return extractContent(rawResponse, provider)
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun parseErrorMessage(rawError: String): String {
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawError)
        return match?.groupValues?.get(1) ?: rawError.take(200)
    }

    private fun extractContent(json: String, provider: Provider): String {
        val startMarker = if (provider == Provider.ANTHROPIC) "\"text\": \"" else "\"content\": \""
        val start = json.indexOf(startMarker)
        if (start == -1) {
            val errorMarker = "\"message\": \""
            val errStart = json.indexOf(errorMarker)
            if (errStart != -1) {
                return "API Message: " + unescapeJsonSubstring(json, errStart + errorMarker.length)
            }
            return "Unable to parse model response."
        }

        return unescapeJsonSubstring(json, start + startMarker.length)
    }

    private fun unescapeJsonSubstring(json: String, startIdx: Int): String {
        val sb = StringBuilder()
        var i = startIdx
        var escaped = false

        while (i < json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 4 < json.length) {
                            val hex = json.substring(i + 1, i + 5)
                            try {
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            } catch (ignored: NumberFormatException) {
                                sb.append("\\u").append(hex)
                            }
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append(c)
                }
                escaped = false
            } else {
                if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }
}