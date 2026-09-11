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

import com.hackathon.aihelper.AutoDevManager
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import org.jetbrains.annotations.Nls

class AppSettingsConfigurable : Configurable {

    private var settingsComponent: AppSettingsComponent? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "AI Auto-Dev"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.getPreferredFocusedComponent()
    }

    override fun createComponent(): JComponent? {
        settingsComponent = AppSettingsComponent()
        return settingsComponent?.panel
    }

    override fun isModified(): Boolean {
        val settings = AppSettingsState.getInstance()
        return settingsComponent?.apiKey != settings.apiKey ||
                settingsComponent?.modelName != settings.modelName ||
                settingsComponent?.customApiUrl != settings.customApiUrl ||
                settingsComponent?.enableGhostText != settings.enableGhostText ||
                settingsComponent?.paranoidMode != settings.paranoidMode
    }

    override fun apply() {
        val settings = AppSettingsState.getInstance()
        settings.apiKey = settingsComponent?.apiKey ?: ""
        settings.modelName = settingsComponent?.modelName ?: "gpt-4o"
        settings.customApiUrl = settingsComponent?.customApiUrl ?: ""
        settings.enableGhostText = settingsComponent?.enableGhostText ?: true
        settings.paranoidMode = settingsComponent?.paranoidMode ?: false

        if (settings.enableGhostText) {
            AutoDevManager.start()
        } else {
            AutoDevManager.stop()
        }
    }

    override fun reset() {
        val settings = AppSettingsState.getInstance()
        settingsComponent?.apiKey = settings.apiKey
        settingsComponent?.modelName = settings.modelName
        settingsComponent?.customApiUrl = settings.customApiUrl
        settingsComponent?.enableGhostText = settings.enableGhostText
        settingsComponent?.paranoidMode = settings.paranoidMode
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
}