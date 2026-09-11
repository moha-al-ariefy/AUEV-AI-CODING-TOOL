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

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.aihelper.settings.AppSettingsState
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class AIChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // I added this check to stop Direct3D from crashing the party on Windows.
        // If Skiko fails to link, this usually forces it to behave.
        System.setProperty("skiko.renderApi", "SOFTWARE_COMPAT")

        val content = ComposePanel().apply {
            setContent {
                // I tried to match the Copilot dark theme. It looks slick.
                MaterialTheme(
                    colors = darkColors(
                        primary = Color(0xFF2F80ED),      // Copilot Blue
                        background = Color(0xFF1E1E1E),   // Deep Dark
                        surface = Color(0xFF252526),      // VS Code-ish Panel
                        onSurface = Color(0xFFCCCCCC)     // Text
                    )
                ) {
                    MainScreen(project)
                }
            }
        }
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(content, "", false)
        )
    }
}

enum class Screen { CHAT, SETTINGS }

// I added 'isApplied' state so we can track if the user clicked the button
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val isCodeAction: Boolean = false,
    var isApplied: MutableState<Boolean> = mutableStateOf(false)
)

val LocalFontSize = compositionLocalOf { mutableStateOf(13) }

@Composable
fun MainScreen(project: Project) {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }
    // I am using a snapshot state list so the UI actually updates when I add stuff
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val fontSizeState = remember { mutableStateOf(AppSettingsState.getInstance().chatFontSize) }

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(text = "Hello! I am AUEV. Ready to code safely.", isUser = false))
        }
    }

    CompositionLocalProvider(LocalFontSize provides fontSizeState) {
        when (currentScreen) {
            Screen.CHAT -> ChatView(
                project = project,
                messages = messages,
                onNavigateSettings = { currentScreen = Screen.SETTINGS }
            )
            // I reused the old settings view because I was too lazy to rewrite it
            Screen.SETTINGS -> SettingsView(
                onBack = { currentScreen = Screen.CHAT }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatView(
    project: Project,
    messages: MutableList<ChatMessage>,
    onNavigateSettings: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(AppSettingsState.getInstance().modelName.ifBlank { "gpt-4o" }) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isParanoid = AppSettingsState.getInstance().paranoidMode

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {

        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xFF252526))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUEV", fontWeight = FontWeight.Bold, color = Color(0xFF2F80ED), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                // Paranoid Mode Chip
                Surface(
                    color = if (isParanoid) Color(0xFF1B382B) else Color(0xFF333333),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isParanoid) Color(0xFF2E7D32) else Color(0xFF555555))
                ) {
                    Text(
                        text = if (isParanoid) "🛡️ Paranoid" else "⚡ Standard",
                        color = if (isParanoid) Color(0xFF81C784) else Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Clear Chat Button
                IconButton(
                    onClick = {
                        messages.clear()
                        messages.add(ChatMessage(text = "Chat cleared. Ready for your next mission.", isUser = false))
                    },
                    modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                ) {
                    Text("🗑️", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Settings Button
                IconButton(
                    onClick = onNavigateSettings,
                    modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                ) {
                    Text("⚙", fontSize = 15.sp, color = Color.LightGray)
                }
            }
        }

        // --- QUICK ACTION PILLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickActionButton(label = "🛡️ Audit", tooltip = "Scan file for OWASP vulnerabilities") {
                messages.add(ChatMessage(text = "Running Security Audit on current file...", isUser = true))
                isLoading = true
                statusText = "Auditing file for vulnerabilities..."
                ChatService.runAudit(project) { response ->
                    isLoading = false
                    statusText = ""
                    val isCode = response.contains("```")
                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = isCode))
                }
            }

            QuickActionButton(label = "💡 Explain", tooltip = "Explain selection or file") {
                messages.add(ChatMessage(text = "Explain the current code architecture", isUser = true))
                isLoading = true
                statusText = "Analyzing code structure..."
                ChatService.runExplain(project) { response ->
                    isLoading = false
                    statusText = ""
                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = response.contains("```")))
                }
            }

            QuickActionButton(label = "⚡ Refactor", tooltip = "Clean and optimize code") {
                messages.add(ChatMessage(text = "Refactor code for clean architecture and performance", isUser = true))
                isLoading = true
                statusText = "Refactoring code..."
                ChatService.runRefactor(project) { response ->
                    isLoading = false
                    statusText = ""
                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = response.contains("```")))
                }
            }

            QuickActionButton(label = "🧪 Tests", tooltip = "Generate unit tests") {
                messages.add(ChatMessage(text = "Generate comprehensive unit tests", isUser = true))
                isLoading = true
                statusText = "Synthesizing test suite..."
                ChatService.runGenerateTests(project) { response ->
                    isLoading = false
                    statusText = ""
                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = response.contains("```")))
                }
            }

            QuickActionButton(label = "🔒 Sanitize", tooltip = "Scrub secrets and upgrade crypto") {
                messages.add(ChatMessage(text = "Sanitize secrets and upgrade weak crypto", isUser = true))
                isLoading = true
                statusText = "Sanitizing code..."
                ChatService.runSanitize(project) { response ->
                    isLoading = false
                    statusText = ""
                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = response.contains("```")))
                }
            }
        }

        Divider(color = Color(0xFF2C2C2C))

        // --- CHAT HISTORY ---
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages) { msg ->
                CopilotMessageBubble(
                    message = msg,
                    project = project,
                    onDiscard = { messages.remove(msg) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF2F80ED),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (statusText.isNotBlank()) statusText else "AUEV is thinking...",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- BOTTOM INPUT AREA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3E3E42), RoundedCornerShape(8.dp))
                    .background(Color(0xFF252526), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(
                        color = Color(0xFFE1E1E1),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF2F80ED)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = 200.dp)
                        .onPreviewKeyEvent {
                            if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                                if (it.isShiftPressed) {
                                    false
                                } else {
                                    if (inputText.isNotBlank()) {
                                        val prompt = inputText
                                        messages.add(ChatMessage(text = prompt, isUser = true))
                                        inputText = ""
                                        isLoading = true
                                        statusText = "Generating answer..."
                                        ChatService.sendMessage(project, prompt) { response ->
                                            isLoading = false
                                            statusText = ""
                                            val isCode = response.contains("```") || response.contains("class ") || response.contains("fun ")
                                            messages.add(ChatMessage(text = response, isUser = false, isCodeAction = isCode))
                                        }
                                    }
                                    true
                                }
                            } else {
                                false
                            }
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toolbar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModelPill(selectedModel) {
                        selectedModel = it
                        AppSettingsState.getInstance().modelName = it
                    }

                    IconTextButton(
                        text = "Send ⏎",
                        color = Color(0xFF2F80ED),
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val prompt = inputText
                                messages.add(ChatMessage(text = prompt, isUser = true))
                                inputText = ""
                                isLoading = true
                                statusText = "Generating answer..."
                                ChatService.sendMessage(project, prompt) { response ->
                                    isLoading = false
                                    statusText = ""
                                    val isCode = response.contains("```") || response.contains("class ")
                                    messages.add(ChatMessage(text = response, isUser = false, isCodeAction = isCode))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(label: String, tooltip: String = "", onClick: () -> Unit) {
    Surface(
        color = Color(0xFF2A2D34),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF3C404B)),
        modifier = Modifier
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
    ) {
        Text(
            text = label,
            color = Color(0xFFE1E1E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CopilotMessageBubble(message: ChatMessage, project: Project, onDiscard: () -> Unit) {
    val fontSize = LocalFontSize.current.value.sp
    val isUser = message.isUser
    var isCopied by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2F80ED)),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.widthIn(max = 650.dp)) {
            if (!isUser) {
                Text("AUEV", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (message.isCodeAction) {
                val detectedLang = Regex("```([a-zA-Z0-9_-]+)").find(message.text)?.groupValues?.get(1)?.uppercase() ?: "CODE"

                Card(
                    shape = RoundedCornerShape(6.dp),
                    backgroundColor = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    elevation = 0.dp
                ) {
                    Column {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D2D2D))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(detectedLang, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isCopied) "✓ Copied" else "📋 Copy",
                                color = if (isCopied) Color(0xFF81C784) else Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable {
                                        val selection = StringSelection(ChatService.cleanMarkdown(message.text))
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                        isCopied = true
                                    }
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                            )
                        }

                        // Code Content
                        SelectionContainer {
                            Text(
                                text = ChatService.cleanMarkdown(message.text),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize,
                                color = Color(0xFFCE9178),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Divider(color = Color(0xFF333333))

                        // Action Footer
                        Row(modifier = Modifier.padding(4.dp)) {
                            if (!message.isApplied.value) {
                                Button(
                                    onClick = {
                                        ChatService.applyCodeToCurrentFile(project, message.text)
                                        message.isApplied.value = true
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2F80ED)),
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("⚡ Apply", color = Color.White, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        ChatService.insertCodeAtCaret(project, message.text)
                                        message.isApplied.value = true
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFF4A4A4A)),
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("➕ Caret", fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        ChatService.undoLastAction(project)
                                        message.isApplied.value = false
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("↩ Undo", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = onDiscard,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                                border = BorderStroke(1.dp, Color.Gray),
                                modifier = Modifier.weight(0.5f).height(30.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("✕", fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                if (isUser) {
                    Surface(
                        color = Color(0xFF2B313A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = Color(0xFFE1E1E1),
                            fontSize = fontSize,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = Color(0xFFCCCCCC),
                            fontSize = fontSize,
                            lineHeight = (fontSize.value * 1.5).sp
                        )
                    }
                }
            }
        }
    }
}

// --- MICRO COMPONENTS ---

@Composable
fun ModelPill(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet", "llama-3.3-70b-versatile")

    Box {
        Surface(
            color = Color(0xFF333333),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .clickable { expanded = true }
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected, color = Color.White, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("▾", color = Color.Gray, fontSize = 10.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF252526))
        ) {
            items.forEach { label ->
                DropdownMenuItem(onClick = {
                    onSelect(label)
                    expanded = false
                }) {
                    Text(label, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun IconTextButton(text: String, color: Color = Color.Gray, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
    )
}

@Composable
fun SettingsView(onBack: () -> Unit) {
    val settings = AppSettingsState.getInstance()
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var customApiUrl by remember { mutableStateOf(settings.customApiUrl) }
    var enableGhost by remember { mutableStateOf(settings.enableGhostText) }
    var enableParanoid by remember { mutableStateOf(settings.paranoidMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, modifier = Modifier.clickable { onBack() }.padding(end = 8.dp))
            Text("AUEV Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))

        // API Key
        Text("API Key (OpenAI / Anthropic / Groq)", color = Color.Gray, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF4E5155), RoundedCornerShape(4.dp))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Custom API Base URL
        Text("Custom API Base URL (Ollama / Local / OpenRouter)", color = Color.Gray, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = customApiUrl,
            onValueChange = { customApiUrl = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF4E5155), RoundedCornerShape(4.dp))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Toggles
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enableGhost, onCheckedChange = { enableGhost = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2F80ED)))
            Text("Enable Ghost Text (The spectral pair programmer)", color = Color(0xFFBBBBBB), fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enableParanoid, onCheckedChange = { enableParanoid = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2F80ED)))
            Text("Paranoid Mode (Strict OWASP tripwires & secret checks)", color = Color(0xFFBBBBBB), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                settings.apiKey = apiKey
                settings.customApiUrl = customApiUrl
                settings.enableGhostText = enableGhost
                settings.paranoidMode = enableParanoid
                if (enableGhost) com.hackathon.aihelper.AutoDevManager.start() else com.hackathon.aihelper.AutoDevManager.stop()
                onBack()
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2F80ED)),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text("Save & Exit", color = Color.White)
        }
    }
}