# AUEV (AI Unified Editor Vision) - Beta 0.6

**AUEV** is a **Security-First** AI development suite for **IntelliJ IDEA** and **Visual Studio Code**.

[🌐 Visit the Official Website](https://auev-bymrsheep.vercel.app/)

While other plugins just generate code, AUEV audits it. We combine the speed of "Ghost Text" with a **Paranoid Security Architecture** designed to stop vulnerabilities *before* you commit them.

**"Code fast, don't get hacked."**

![Status](https://img.shields.io/badge/Status-Beta_0.6-orange) ![Focus](https://img.shields.io/badge/Focus-Security_First-red) ![Platforms](https://img.shields.io/badge/Platforms-IntelliJ%20%7C%20VS%20Code-blue) ![License](https://img.shields.io/badge/License-Apache_2.0-green)

## The Security Pivot
We have shifted our core focus. AUEV is no longer just a coding assistant; it is a **guardian**.
* **Local Secret Scanning:** Aggressive local heuristics intercept and block the AI if it attempts to hallucinate or hardcode secrets (AWS, GitHub, OpenAI keys, private keys, JWTs).
* **Auto-Sanitization & Crypto Upgrades:** The "Ghost" engine actively patches weak algorithms (e.g., auto-upgrading MD5/SHA-1 to SHA-256) across Java, Kotlin, Python, and TypeScript.
* **Strict Syntax Validation:** Hooks directly into IntelliJ's PSI tree to actively reject invalid syntax and spaghetti code before it pollutes your editor.
* **Paranoid Mode:** A dedicated setting that injects strict OWASP Top 10 security guidelines into every AI prompt.

---

## Key Features

### 1. Security Auto-Audit
* **Instant Risk Report:** Click **🛡️ Audit** in the sidebar (or press `Ctrl+Alt+S`) to scan your open file.
* **OWASP Top 10 Focus:** Detects hardcoded secrets, unchecked inputs, and dangerous SQL patterns.
* **Fix It For Me:** The audit generates patched code ready for one-click application.

### 2. Toggleable Smart Ghost Text
* **Dual-Agent Engine:** One agent writes the code, the second agent (The "Sanitizer") cleans it.
* **Kill Switch:** Ghost text can be **toggled ON/OFF** in settings or status bar.
* **Smart Zipper:** Prevents the AI from repeating code you've already written.

### 3. Multi-Provider Intelligence (BYOK)
* **Bring Your Own Key:** We don't act as a middleman. Your keys, your data.
    * **OpenAI:** Best for logic and security auditing (`gpt-4o`, `gpt-4o-mini`).
    * **Anthropic:** Superior for large-scale refactoring (`claude-3-5-sonnet`).
    * **Groq (Llama 3.3):** Ultra-low latency for instant Ghost Text (`llama-3.3-70b-versatile`).
    * **Local / Ollama / Custom:** Connect any local LLM server via custom API Base URL.

### 4. Modern Native UI (JetBrains + VS Code)
* **Quick Actions:** Instant one-click triggers for **🛡️ Audit**, **💡 Explain**, **⚡ Refactor**, **🧪 Tests**, and **🔒 Sanitize**.
* **Context-Aware:** The chat knows your current file and active selection.
* **One-Click Apply:** Found a fix in chat? Click **⚡ Apply** to replace selection or **➕ Caret** to inject directly into the editor.
* **Full VS Code Extension:** Check out the `vscode/` folder for the complete VS Code edition with `.vsix` ready to install!

---

## Installation & Setup

### For IntelliJ IDEA:
1. Clone repo -> Run `./gradlew buildPlugin` -> Install the ZIP from `build/distributions/AUEV-0.6-BETA-2026.zip`.
2. Configure your API key or custom endpoint in the **AUEV** sidebar (`⚙ Settings`).

### For Visual Studio Code:
1. Navigate to `vscode/` directory.
2. Run `npm install && npm run package`.
3. Install extension: `code --install-extension auev-0.6.0.vsix` (or drag & drop the VSIX into VS Code).

---

## Tech Stack
* **JetBrains:** Kotlin (JVM 21), Jetpack Compose for Desktop, IntelliJ Platform SDK 2024.3 - 2025.3.
* **VS Code:** TypeScript, VS Code Extensibility APIs, InlineCompletionItemProvider, WebviewViewProvider.
* **Architecture:** Event-Driven (EditorFactoryListener) with Asynchronous AI Execution and PSI Tree Analysis.

## License
This project is licensed under the **Apache License 2.0**. See the `LICENSE` file for details.

---
*Was Built for the JetBrains UOBD_GDG Hackathon 2026.*
*Moved as a main project*