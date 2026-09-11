# AUEV (AI Coding Tool) - v0.7 The Secure Coding Suite

**AUEV** is an elite **Security-First** AI development suite engineered for **JetBrains IDEs** (IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, Android Studio) and **Visual Studio Code**.

[🌐 Visit the Official Website](https://auev-bymrsheep.vercel.app/)

While conventional coding assistants blindly autocomplete whatever pattern they predict, **AUEV prioritizes defensive engineering**. We integrate low-latency **Smart Ghost Text** with a comprehensive **Paranoid Security Architecture** designed to detect vulnerabilities, model threats, and audit dependencies *before* code ever touches production.

**"Code fast, don't get hacked."**

![Status](https://img.shields.io/badge/Status-v0.7_Release-success) ![Focus](https://img.shields.io/badge/Focus-Secure_Coding_Suite-red) ![Platforms](https://img.shields.io/badge/Platforms-JetBrains%20%7C%20VS%20Code-blue) ![License](https://img.shields.io/badge/License-Apache_2.0-green)

---

## 🛡️ The Secure Coding Suite (v0.7)

### 1. 🛡️ SAST Security Audit & Scorecard
* **Deep Code Scanning:** Analyzes active editor buffer and selections for OWASP Top 10 risks, CWE classifications, and logic flaws.
* **Security Scorecard:** Generates clear security grades (**A+** through **F**), Exploitability Index, CVSS estimate, and risk severity ratings.
* **One-Click Remediation:** Delivers fully patched code blocks with one-click **⚡ Apply** or **➕ Caret** injection into your active file.

### 2. 🎯 STRIDE Threat Modeling
* **Architectural Threat Assessment:** Runs full STRIDE analysis against open modules and services:
  * **[S] Spoofing:** Authentication boundaries, identity impersonation risks.
  * **[T] Tampering:** Parameter tampering, data corruption, integrity compromises.
  * **[R] Repudiation:** Missing audit logging and accountability gaps.
  * **[I] Information Disclosure:** Secret leaks, side-channels, stack trace leakage.
  * **[D] Denial of Service:** Algorithmic complexity, unconstrained allocations, ReDoS.
  * **[E] Elevation of Privilege:** Broken access controls, IDOR, path traversal.
* **STRIDE Threat Matrix:** Produces a structured threat table with trust boundaries and concrete mitigations.

### 3. 📦 Supply Chain & Dependency CVE Auditor
* **Manifest & Package Inspection:** Scans `package.json`, `pom.xml`, `build.gradle`, `requirements.txt`, `Cargo.toml`, and `go.mod`.
* **CVE & Risk Detection:** Flags known vulnerable packages, floating version ranges, typosquatting vectors, and suspicious lifecycle scripts.
* **Safe Pin Recommendations:** Generates pinned, verified manifest replacements and secure import alternatives.

### 4. 🔒 Input Validation & Contract Shield
* **Boundary Enforcement:** Automatically identifies all external input vectors (HTTP parameters, body payloads, CLI args, file streams).
* **Defensive Schema Synthesis:** Generates type-safe schema validators and contract assertions in idiomatic frameworks:
  * **TypeScript/JavaScript:** Zod, Joi, TypeBox
  * **Python:** Pydantic, Marshmallow
  * **Java/Kotlin:** Hibernate Validator, Jakarta Bean Validation
  * **Rust / Go:** Serde validation attributes, go-playground/validator

### 5. 🧹 Secret Sanitizer & Crypto Upgrades
* **Local Heuristic Tripwires:** Detects AWS tokens, GitHub PATs, OpenAI/Anthropic/Groq keys, JWTs, and private keys.
* **Crypto Hardening:** Automatically upgrades obsolete hashing/ciphers (MD5, SHA-1, DES -> SHA-256+, Argon2, bcrypt, AES-GCM).

---

## 🤖 Dynamic Model Registry & BYOK

AUEV does not lock you into a proprietary wrapper. Bring your own keys and connect directly to whatever intelligence you choose:

* **Preset Cloud Providers:**
  * **OpenAI:** `gpt-4o`, `gpt-4o-mini`, `o1`
  * **Anthropic:** `claude-3-5-sonnet-20240620`, `claude-3-5-haiku-20241022`
  * **Groq:** `llama-3.3-70b-versatile` (ultra-low latency Ghost Text)
* **Custom Model Registry:**
  * Dynamically add custom models directly via the in-editor model dropdown:
    * **DeepSeek:** `deepseek-chat`, `deepseek-reasoner`, `deepseek/deepseek-r1`
    * **Qwen:** `qwen/qwen-2.5-coder-32b`
    * **Ollama / Local LLM:** Point to `http://localhost:11434/v1`
    * **OpenRouter:** Point to `https://openrouter.ai/api/v1`

---

## 💻 Dual IDE Support: JetBrains + VS Code

### JetBrains Plugin (IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, Android Studio)
* **Compose for Desktop UI:** Native dark-themed panel matching JetBrains look-and-feel.
* **Ghost Text Engine:** Integrated with IntelliJ's document caret and undo manager.
* **Distribution Asset:** `build/distributions/AUEV-0.7-2026.zip`

### Visual Studio Code Extension
* **Modern Webview Assistant:** Custom sidebar with security scorecard badges and quick actions.
* **Native Inline Completions:** Implements `vscode.languages.registerInlineCompletionItemProvider`.
* **Distribution Asset:** `vscode/auev-0.7.0.vsix`

---

## 🚀 Installation & Building

### 1. Build JetBrains Distribution (.zip)
```bash
./gradlew buildPlugin
# Output: build/distributions/AUEV-0.7-2026.zip
```
In IntelliJ / Android Studio:
* Settings -> Plugins -> ⚙ Gear Icon -> **Install Plugin from Disk...** -> Select `AUEV-0.7-2026.zip`.

### 2. Build VS Code Distribution (.vsix)
```bash
cd vscode
npm install
npm run compile
npx @vscode/vsce package --no-dependencies
# Output: vscode/auev-0.7.0.vsix
```
In VS Code:
```bash
code --install-extension auev-0.7.0.vsix
```
Or in the Extensions view, click `...` (Views and More Actions) -> **Install from VSIX...**.

---

## ⚙ Configuration Quick Reference

| Setting | JetBrains XML / UI | VS Code Config (`settings.json`) | Description |
| :--- | :--- | :--- | :--- |
| **API Key** | Sidebar ⚙ -> API Key | `auev.apiKey` | BYOK for OpenAI, Anthropic, or Groq |
| **Active Model** | Model Dropdown | `auev.model` | Selected model identifier |
| **Custom Models** | Sidebar ⚙ / Model Dropdown | `auev.customModels` | Comma-separated list of custom models |
| **Custom API Base URL**| Sidebar ⚙ -> Base URL | `auev.customApiUrl` | Ollama, OpenRouter, or Local LLM endpoint |
| **Paranoid Mode** | Top Bar Toggle / Settings | `auev.paranoidMode` | Injects strict OWASP & threat rules into AI prompts |
| **Ghost Text** | Settings -> Enable Ghost | `auev.enableGhostText` | Toggles inline predictive ghost completion |

---

## 📜 License
This project is licensed under the **Apache License 2.0**. See the [LICENSE](file:///home/ma9802234/githubproject/AUEV-AI-CODING-TOOL/LICENSE) file for details.