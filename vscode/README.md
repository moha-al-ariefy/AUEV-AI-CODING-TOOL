# AUEV for Visual Studio Code (Beta 0.6)

**AUEV (AI Unified Editor Vision)** is a **Security-First** AI development suite now available for **Visual Studio Code**.

[🌐 Official Website](https://auev-bymrsheep.vercel.app/)

While ordinary AI assistants just hallucinate code, **AUEV audits and secures it**. We combine the speed of inline ghost text completions with a **Paranoid Security Architecture** designed to stop vulnerabilities, SQL injections, and leaked secrets *before* you commit them.

> **"Code fast, don't get hacked."**

---

## ⚡ Key Features

### 1. Toggleable Smart Ghost Text
* **Dual-Agent Engine:** Writes completions with sub-second latency while the sanitizer scrubs syntax flaws and repetitions.
* **Smart Zipper:** Prevents the AI from repeating braces or boilerplate you have already typed.
* **Kill Switch:** Quickly toggle ghost text with `AUEV: Toggle Ghost Text On/Off`.

### 2. Local Secret Tripwires
* Aggressive local regex heuristics intercept the AI if it attempts to hallucinate or leak:
  * AWS Access Keys (`AKIA...`)
  * GitHub Personal Access Tokens (`ghp_...`, `github_pat_...`)
  * OpenAI / Anthropic / Groq API Keys
  * SSH / RSA Private Keys
  * Database connection strings with embedded passwords

### 3. Automated Crypto & Sanitization Upgrades
* Automatically catches weak cryptographic algorithms (e.g. upgrading MD5/SHA-1 to SHA-256) across TypeScript, JavaScript, Python, and Java.

### 4. Paranoid Mode (OWASP Top 10)
* Enforces strict OWASP Top 10 guidelines in every prompt.
* Refuses insecure patterns (raw SQL concatenations, command injections, unsafe deserialization) and delivers hardened alternatives.

### 5. Multi-Provider BYOK (Bring Your Own Key)
* **OpenAI:** `gpt-4o`, `gpt-4o-mini`, `o1`
* **Anthropic:** `claude-3-5-sonnet`, `claude-3-5-haiku`
* **Groq:** `llama-3.3-70b-versatile` (blazing fast for ghost text)
* **Local / Custom:** Ollama, OpenRouter, LM Studio via custom API Base URL.

### 6. Modern Native Sidebar Assistant
* Interactive dark-themed Chat Assistant matching the AUEV design language.
* **Quick Actions:**
  * 🛡️ **Audit File** (`Ctrl+Alt+S` / `Cmd+Alt+S`)
  * 💡 **Explain Selection**
  * ⚡ **Refactor & Clean**
  * 🧪 **Generate Tests**
  * 🔒 **Sanitize Secrets**
* **One-Click Insert & Replace:**
  * ⚡ **Apply / Replace** (replaces selection or file)
  * ➕ **Insert at Caret**
  * 📋 **Copy Code**

---

## 🛠️ Installation & Building

```bash
cd vscode
npm install
npm run compile
npm run package # Produces auev-0.6.0.vsix
```

Install into VS Code:
```bash
code --install-extension auev-0.6.0.vsix
```

---

## ⚙️ Configuration

Open VS Code Settings (`Ctrl+,` / `Cmd+,`) and search for `AUEV`:

| Setting | Type | Description |
|---|---|---|
| `auev.apiKey` | `string` | Your OpenAI, Anthropic, or Groq API Key |
| `auev.model` | `string` | Target model (`gpt-4o`, `claude-3-5-sonnet`, `llama-3.3-70b-versatile`) |
| `auev.customApiUrl` | `string` | Custom API base URL (e.g., `http://localhost:11434/v1/chat/completions`) |
| `auev.enableGhostText` | `boolean` | Enable or disable inline ghost text |
| `auev.paranoidMode` | `boolean` | Enable strict OWASP compliance & secret shields |

---

## ⌨️ Default Keybindings

* **Open AUEV Sidebar:** `Ctrl+Alt+A` / `Cmd+Alt+A`
* **Run Security Audit:** `Ctrl+Alt+S` / `Cmd+Alt+S`
* **Accept Ghost Text:** `Tab`
* **Dismiss Ghost Text:** `Escape`
