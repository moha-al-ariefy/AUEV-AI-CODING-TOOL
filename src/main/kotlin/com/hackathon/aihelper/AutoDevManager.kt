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

import com.hackathon.aihelper.settings.AppSettingsState
import com.intellij.openapi.Disposable // <--- NEW FRIEND
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer // <--- THE REAPER
import com.intellij.util.Alarm
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

object AutoDevManager : EditorFactoryListener {

    // I suppressed the warning because I know what I'm doing (mostly)
    @Suppress("DEPRECATION")
    private val alarm = Alarm()
    private val LOG = Logger.getInstance(AutoDevManager::class.java)

    // I use this 'Disposable' token to control the listener's life force
    private var listenerDisposable: Disposable? = null
    private var isRegistered = false

    val currentMergeFix = java.util.WeakHashMap<Editor, GhostSanitizer.MergeResult>()
    val currentInlays = java.util.WeakHashMap<Editor, Inlay<*>>()

    // --- THE LIGHT SWITCH (REMASTERED) ---

    fun start() {
        if (isRegistered) return

        LOG.warn("👻 [AutoDev] I am waking up... Creating new listener lifecycle.")

        // 1. Create a new "Life" for this listener session
        // Note for 2025: We must be careful not to leak this.
        val newDisposable = Disposer.newDisposable("AutoDevListener")
        listenerDisposable = newDisposable

        // 2. Register the listener attached to this disposable.
        // When we dispose 'newDisposable', IntelliJ automatically unhooks the listener. Magic.
        EditorFactory.getInstance().addEditorFactoryListener(this, newDisposable)

        // 3. Hook into EXISTING editors (The "Chicken and Egg" Fix)
        val openEditors = EditorFactory.getInstance().allEditors
        for (editor in openEditors) {
            LOG.warn("👻 [AutoDev] Found open editor: ${editor.document}. Hooking in.")
            hookIntoEditor(editor)
        }

        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return

        LOG.warn("💤 [AutoDev] I am going to sleep... Killing listener.")

        // Instead of 'removeListener', we just Dispose the parent.
        listenerDisposable?.let {
            // I suppressed this because "isDisposed" is deprecated but it still works fine
            @Suppress("DEPRECATION")
            if (!Disposer.isDisposed(it)) {
                Disposer.dispose(it)
            }
            listenerDisposable = null
        }

        // Cleanup the paint
        val editors = EditorFactory.getInstance().allEditors
        for (editor in editors) {
            resetSuggestion(editor)
        }
        isRegistered = false
    }

    // --- LISTENER LOGIC ---

    private fun hookIntoEditor(editor: Editor) {
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                resetSuggestion(editor)
                alarm.cancelAllRequests()
                if (event.newFragment.length > 100) return
                alarm.addRequest({ fetchSuggestion(editor) }, 600)
            }
        })
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        hookIntoEditor(event.editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) = resetSuggestion(event.editor)

    private fun fetchSuggestion(editor: Editor) {
        // In 2025, pooled threads are still okay for network, but Coroutines are preferred.
        // I'm keeping pooled threads here to avoid rewriting the whole logic.
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = AppSettingsState.getInstance()

            if (!settings.enableGhostText) return@executeOnPooledThread

            if (settings.apiKey.isBlank()) {
                LOG.warn("⚠️ [AutoDev] API Key is MISSING. Please check settings.")
                return@executeOnPooledThread
            }

            var fullContext = ""
            var currentLinePrefix = ""
            var suffix = ""

            val shouldProceed = ApplicationManager.getApplication().runReadAction<Boolean> {
                if (editor.isDisposed) return@runReadAction false
                val offset = editor.caretModel.offset
                val text = editor.document.text

                val start = (offset - 2500).coerceAtLeast(0)
                fullContext = text.substring(start, offset)

                val lineStart = editor.document.getLineStartOffset(editor.caretModel.logicalPosition.line)
                currentLinePrefix = text.substring(lineStart, offset).trim()

                val suffixEnd = (offset + 1000).coerceAtMost(text.length)
                suffix = text.substring(offset, suffixEnd)

                return@runReadAction true
            }

            if (!shouldProceed || fullContext.isBlank()) return@executeOnPooledThread

            try {
                val provider = when {
                    settings.apiKey.startsWith("sk-ant-") -> Provider.ANTHROPIC
                    settings.apiKey.startsWith("gsk_") -> Provider.GROQ
                    else -> Provider.OPENAI
                }

                LOG.warn("🚀 [AutoDev] Requesting completion from ${provider.name}...")
                // I passed the paranoidMode setting to callAI so the ghost knows to behave
                val rawSuggestion = callAI(provider, settings.apiKey, settings.modelName, fullContext, suffix, settings.paranoidMode)

                // --- THE LOCAL HEURISTIC SHIELD ---
                // I added this so we aren't liars on LinkedIn. If the AI hallucinates a secret, we snipe it locally.
                if (containsSecrets(rawSuggestion)) {
                    LOG.warn("🚨 [AutoDev] SECURITY TRIPWIRE: AI tried to leak a secret or hardcoded password. Ghost text blocked.")
                    return@executeOnPooledThread
                }

                if (rawSuggestion.isNotBlank()) {
                    val fix = GhostSanitizer.sanitize(currentLinePrefix, suffix, rawSuggestion)

                    if (fix.textToInsert.isNotBlank()) {
                        LOG.warn("✅ [AutoDev] Ghost Text Ready: '${fix.textToInsert}'")
                        ApplicationManager.getApplication().invokeLater {
                            renderGhostText(editor, fix)
                        }
                    }
                }
            } catch (e: Exception) {
                LOG.warn("[AutoDev] 💥 API Error: ${e.message}")
            }
        }
    }

    private fun renderGhostText(editor: Editor, fix: GhostSanitizer.MergeResult) {
        if (editor.isDisposed) return
        resetSuggestion(editor)
        currentMergeFix[editor] = fix
        val offset = editor.caretModel.offset

        try {
            val inlay = editor.inlayModel.addInlineElement(offset, GhostInlayRenderer(fix.textToInsert))
            if (inlay != null) currentInlays[editor] = inlay
        } catch (ignored: Exception) {
            // I renamed 'e' to 'ignored' so the compiler stops yelling at me.
            // Sometimes the editor closes while I am painting. It happens.
        }
    }

    fun resetSuggestion(editor: Editor) {
        currentInlays[editor]?.dispose()
        currentInlays.remove(editor)
        currentMergeFix.remove(editor)
    }

    enum class Provider { OPENAI, ANTHROPIC, GROQ }

    private fun callAI(provider: Provider, apiKey: String, model: String, prefix: String, suffix: String, isParanoid: Boolean): String {
        var sysPrompt = """
            You are a low-latency code completion engine.
            Complete the code at the [CURSOR] position.
            - Output ONLY the missing code. 
            - No markdown.
            - No repetition of code found in the SUFFIX.
            - Maintain indentation.
            - If user typed a shortcut (e.g. 'sysout'), expand it fully.
        """.trimIndent()

        // I added the paranoid mode rules for the ghost
        if (isParanoid) {
            sysPrompt += """
                
                🚨 SECURITY OVERRIDE: 
                - DO NOT autocomplete or suggest insecure patterns (e.g., hardcoded credentials, SQL injection vulnerabilities, weak cryptography).
                - If the context implies an insecure operation, return an empty string to refuse completion.
            """.trimIndent()
        }

        val userContent = "PREFIX:\n$prefix\n\n[CURSOR]\n\nSUFFIX:\n$suffix"

        val customUrl = AppSettingsState.getInstance().customApiUrl.trim()
        val urlStr = if (customUrl.isNotBlank()) {
            customUrl
        } else {
            when (provider) {
                Provider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
                Provider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                Provider.OPENAI -> "https://api.openai.com/v1/chat/completions"
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
        conn.connectTimeout = 4000
        conn.readTimeout = 7000

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
                "max_tokens": 128,
                "system": "${escapeJson(sysPrompt)}",
                "messages": [
                    {"role": "user", "content": "${escapeJson(userContent)}"}
                ],
                "temperature": 0.1
            }
            """.trimIndent()
        } else {
            """
            {
                "model": "$actualModel",
                "messages": [
                    {"role": "system", "content": "${escapeJson(sysPrompt)}"},
                    {"role": "user", "content": "${escapeJson(userContent)}"}
                ],
                "max_tokens": 128,
                "temperature": 0.1,
                "stop": ["SUFFIX"] 
            }
            """.trimIndent()
        }

        conn.outputStream.use { os -> os.write(jsonInput.toByteArray(StandardCharsets.UTF_8)) }

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
            LOG.warn("[AutoDev] API Error ${conn.responseCode}: $error")
            return ""
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        return extractContent(response, provider)
    }

    private fun escapeJson(text: String) = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")

    private fun extractContent(json: String, provider: Provider): String {
        val searchMarker = if (provider == Provider.ANTHROPIC) "\"text\": \"" else "\"content\": \""
        val start = json.indexOf(searchMarker)
        if (start == -1) return ""
        val actualStart = start + searchMarker.length

        val sb = StringBuilder()
        var i = actualStart
        var escaped = false

        while (i < json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); '\\' -> sb.append('\\'); '"' -> sb.append('"'); else -> sb.append(c)
                }
                escaped = false
            } else {
                if (c == '\\') escaped = true else if (c == '"') break else sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    // --- SECURITY SCANNER ---

    // Enhanced regex scanner to catch secrets, private keys, database URLs, and API tokens
    private fun containsSecrets(text: String): Boolean {
        val secretPatterns = listOf(
            Regex("AKIA[0-9A-Z]{16}"), // AWS Access Key pattern
            Regex("sk-[a-zA-Z0-9]{48,}"), // OpenAI Key pattern
            Regex("sk-proj-[a-zA-Z0-9\\-_]{48,}"), // OpenAI Project Key pattern
            Regex("sk-ant-[a-zA-Z0-9\\-_]{32,}"), // Anthropic Key pattern
            Regex("gsk_[a-zA-Z0-9]{48,}"), // Groq Key pattern
            Regex("ghp_[a-zA-Z0-9]{36}"), // GitHub Classic PAT
            Regex("github_pat_[a-zA-Z0-9_]{82}"), // GitHub Fine-grained PAT
            Regex("-----BEGIN [A-Z ]+PRIVATE KEY-----"), // RSA / EC / SSH private key
            Regex("ey[A-Za-z0-9-_]{10,}\\.ey[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,}"), // JWT Token
            Regex("(?i)(postgres|mysql|mongodb|redis)://[^:]+:[^@]+@"), // DB connection URI with password
            Regex("(?i)(password|secret|token|api[_-]?key)[\"']?\\s*[:=]\\s*[\"'][a-zA-Z0-9_\\-@!#\$%^&*]{8,}[\"']") // Catch-all for hardcoded credentials
        )
        return secretPatterns.any { it.containsMatchIn(text) }
    }
}