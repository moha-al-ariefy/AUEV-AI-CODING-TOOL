/*
 *    Copyright 2026 moha-al-ariefy
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.hackathon.aihelper.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AppSettingsComponent {

    val panel: JPanel
    private val apiKeyField = JBTextField()
    private val modelField = JBTextField()

    private val customApiUrlField = JBTextField()

    // Checkboxes for the new powers
    private val enableGhostTextCheckbox = JBCheckBox("Enable Ghost Text (The spectral pair programmer)")
    private val paranoidModeCheckbox = JBCheckBox("Paranoid Mode (Inject strict OWASP guidelines & tripwires)")

    init {
        // Smart Auto-Detect Logic
        apiKeyField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { detect() }
            override fun removeUpdate(e: DocumentEvent?) { detect() }
            override fun changedUpdate(e: DocumentEvent?) { detect() }

            fun detect() {
                val key = apiKeyField.text.trim()
                if (key.isEmpty()) return

                // 1. Anthropic (Claude)
                if (key.startsWith("sk-ant-")) {
                    modelField.text = "claude-3-5-sonnet-20240620"
                }
                // 2. Groq (Llama 3.3 - Insanely fast)
                else if (key.startsWith("gsk_")) {
                    modelField.text = "llama-3.3-70b-versatile"
                }
                // 3. OpenAI (Standard)
                else if (key.startsWith("sk-")) {
                    if (modelField.text.isBlank() || !modelField.text.startsWith("gpt")) {
                        modelField.text = "gpt-4o"
                    }
                }
            }
        })

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API Key (OpenAI / Anthropic / Groq):"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Model Name (Auto-Detected):"), modelField, 1, false)
            .addLabeledComponent(JBLabel("Custom API Base URL (Ollama / OpenRouter / Local):"), customApiUrlField, 1, false)
            .addSeparator() // Make it look fancy
            .addComponent(enableGhostTextCheckbox, 1)
            .addComponent(paranoidModeCheckbox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    fun getPreferredFocusedComponent() = apiKeyField

    var apiKey: String
        get() = apiKeyField.text
        set(newText) { apiKeyField.text = newText }

    var modelName: String
        get() = modelField.text
        set(newText) { modelField.text = newText }

    var customApiUrl: String
        get() = customApiUrlField.text
        set(newText) { customApiUrlField.text = newText }

    // Getters and Setters for the checkboxes
    var enableGhostText: Boolean
        get() = enableGhostTextCheckbox.isSelected
        set(newStatus) { enableGhostTextCheckbox.isSelected = newStatus }

    var paranoidMode: Boolean
        get() = paranoidModeCheckbox.isSelected
        set(newStatus) { paranoidModeCheckbox.isSelected = newStatus }
}