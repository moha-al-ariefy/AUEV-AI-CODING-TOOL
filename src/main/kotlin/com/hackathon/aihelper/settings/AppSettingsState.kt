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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.hackathon.aihelper.settings.AppSettingsState",
    storages = [Storage("AIHelperSettings.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {

    var apiKey: String = ""
    var modelName: String = "gpt-4o" // Default

    // Custom endpoint (Ollama, OpenRouter, Local LLM)
    var customApiUrl: String = ""
    var customModelName: String = ""

    // I added this switch because sometimes the ghost gets too clingy
    var enableGhostText: Boolean = true

    // Paranoid Mode: Strict OWASP & Security tripwires
    var paranoidMode: Boolean = true
    var chatFontSize: Int = 13
    companion object {
        fun getInstance(): AppSettingsState {
            return ApplicationManager.getApplication().getService(AppSettingsState::class.java)
        }
    }

    override fun getState(): AppSettingsState {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}