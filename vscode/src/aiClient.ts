import * as vscode from "vscode";

export enum Provider {
  OPENAI = "OPENAI",
  ANTHROPIC = "ANTHROPIC",
  GROQ = "GROQ",
  CUSTOM = "CUSTOM"
}

export class AuevAiClient {
  public static getApiKey(): string {
    const config = vscode.workspace.getConfiguration("auev");
    return config.get<string>("apiKey", "").trim();
  }

  public static getModel(): string {
    const config = vscode.workspace.getConfiguration("auev");
    return config.get<string>("model", "gpt-4o").trim();
  }

  public static getCustomModels(): string[] {
    const config = vscode.workspace.getConfiguration("auev");
    const raw = config.get<string | string[]>("customModels", "deepseek/deepseek-r1,qwen/qwen-2.5-coder-32b,claude-3-5-haiku-20241022");
    if (Array.isArray(raw)) {
      return raw.map(s => s.trim()).filter(Boolean);
    }
    return String(raw).split(",").map(s => s.trim()).filter(Boolean);
  }

  public static getAvailableModels(): string[] {
    const presets = [
      "gpt-4o",
      "gpt-4o-mini",
      "o1",
      "claude-3-5-sonnet-20240620",
      "claude-3-5-haiku-20241022",
      "llama-3.3-70b-versatile",
      "deepseek-chat",
      "deepseek-reasoner"
    ];
    const custom = this.getCustomModels();
    return Array.from(new Set([...presets, ...custom]));
  }

  public static async addCustomModel(model: string): Promise<void> {
    const clean = model.trim();
    if (!clean) return;
    const current = this.getCustomModels();
    if (!current.includes(clean)) {
      current.push(clean);
      const config = vscode.workspace.getConfiguration("auev");
      await config.update("customModels", current.join(","), vscode.ConfigurationTarget.Global);
    }
    const config = vscode.workspace.getConfiguration("auev");
    await config.update("model", clean, vscode.ConfigurationTarget.Global);
  }

  public static getCustomApiUrl(): string {
    const config = vscode.workspace.getConfiguration("auev");
    return config.get<string>("customApiUrl", "").trim();
  }

  public static isParanoidMode(): boolean {
    const config = vscode.workspace.getConfiguration("auev");
    return config.get<boolean>("paranoidMode", true);
  }

  public static isGhostEnabled(): boolean {
    const config = vscode.workspace.getConfiguration("auev");
    return config.get<boolean>("enableGhostText", true);
  }

  public static getProvider(): Provider {
    const customUrl = this.getCustomApiUrl();
    if (customUrl.length > 0) {
      return Provider.CUSTOM;
    }
    const key = this.getApiKey();
    if (key.startsWith("sk-ant-")) {
      return Provider.ANTHROPIC;
    }
    if (key.startsWith("gsk_")) {
      return Provider.GROQ;
    }
    return Provider.OPENAI;
  }

  /**
   * Complete ghost text with ultra-low latency
   */
  public static async completeGhost(
    prefix: string,
    suffix: string,
    abortSignal?: AbortSignal
  ): Promise<string> {
    const apiKey = this.getApiKey();
    const customUrl = this.getCustomApiUrl();

    if (!apiKey && !customUrl) {
      return "";
    }

    const provider = this.getProvider();
    const isParanoid = this.isParanoidMode();

    let sysPrompt = `You are a low-latency code completion engine.
Complete the code at the [CURSOR] position.
- Output ONLY the missing code.
- No markdown formatting.
- Do not repeat code found in the SUFFIX.
- Maintain proper indentation.`;

    if (isParanoid) {
      sysPrompt += `\n\n🚨 SECURITY OVERRIDE:
- DO NOT autocomplete or suggest insecure patterns (e.g. hardcoded credentials, SQL injection vulnerabilities, weak cryptography).
- If context implies an insecure operation, return an empty string to refuse completion.`;
    }

    const userContent = `PREFIX:\n${prefix}\n\n[CURSOR]\n\nSUFFIX:\n${suffix}`;

    const url = customUrl || (
      provider === Provider.ANTHROPIC
        ? "https://api.anthropic.com/v1/messages"
        : provider === Provider.GROQ
        ? "https://api.groq.com/openai/v1/chat/completions"
        : "https://api.openai.com/v1/chat/completions"
    );

    let model = this.getModel();
    if (provider === Provider.GROQ && (!model || model.startsWith("gpt"))) {
      model = "llama-3.3-70b-versatile";
    } else if (provider === Provider.ANTHROPIC && (!model || model.startsWith("gpt"))) {
      model = "claude-3-5-sonnet-20240620";
    }

    const headers: Record<string, string> = {
      "Content-Type": "application/json"
    };

    let body: any;

    if (provider === Provider.ANTHROPIC) {
      headers["x-api-key"] = apiKey;
      headers["anthropic-version"] = "2023-06-01";
      body = {
        model: model || "claude-3-5-sonnet-20240620",
        max_tokens: 128,
        system: sysPrompt,
        messages: [{ role: "user", content: userContent }],
        temperature: 0.1
      };
    } else {
      if (apiKey) {
        headers["Authorization"] = `Bearer ${apiKey}`;
      }
      body = {
        model: model || "gpt-4o",
        messages: [
          { role: "system", content: sysPrompt },
          { role: "user", content: userContent }
        ],
        max_tokens: 128,
        temperature: 0.1,
        stop: ["SUFFIX"]
      };
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        signal: abortSignal
      });

      if (!response.ok) {
        return "";
      }

      const json: any = await response.json();
      if (provider === Provider.ANTHROPIC) {
        return json.content?.[0]?.text || "";
      } else {
        return json.choices?.[0]?.message?.content || "";
      }
    } catch {
      return "";
    }
  }

  /**
   * Full chat conversation engine
   */
  public static async chat(
    userPrompt: string,
    contextCode: string = "",
    fileName: string = "",
    languageId: string = ""
  ): Promise<string> {
    const apiKey = this.getApiKey();
    const customUrl = this.getCustomApiUrl();

    if (!apiKey && !customUrl) {
      return "⚠️ Please configure your API Key in Settings or the AUEV settings bar.";
    }

    const provider = this.getProvider();
    const isParanoid = this.isParanoidMode();

    let systemPrompt = `You are AUEV (AI Unified Editor Vision), an expert security-first pair programmer integrated into VS Code.
- Deliver clean, robust, highly maintainable production-ready code.
- ALWAYS enclose runnable code in markdown blocks with explicit language tags: \`\`\`language ... \`\`\`.
- Explain WHY decisions were made concisely. Avoid fluff.`;

    if (isParanoid) {
      systemPrompt += `\n\n🚨 STRICT SECURITY OVERRIDE (PARANOID MODE ACTIVE):
- OWASP Top 10 compliance: Prevent SQL Injection, Command Injection, XSS, SSRF, and Broken Auth.
- NEVER output hardcoded credentials, tokens, or private keys.
- MANDATE modern cryptography (SHA-256+, Argon2, bcrypt, AES-GCM). Reject MD5, SHA-1, DES.
- Enforce parameterized queries and strict input validation.
- If an insecure pattern is requested, REFUSE and provide the hardened secure alternative.`;
    }

    const userMessage = `${fileName ? `Active File: ${fileName} (${languageId})\n\n` : ""}${
      contextCode ? `Context Snippet:\n\`\`\`${languageId}\n${contextCode.slice(0, 8000)}\n\`\`\`\n\n` : ""
    }User Request: ${userPrompt}`;

    const url = customUrl || (
      provider === Provider.ANTHROPIC
        ? "https://api.anthropic.com/v1/messages"
        : provider === Provider.GROQ
        ? "https://api.groq.com/openai/v1/chat/completions"
        : "https://api.openai.com/v1/chat/completions"
    );

    let model = this.getModel();
    if (provider === Provider.GROQ && (!model || model.startsWith("gpt"))) {
      model = "llama-3.3-70b-versatile";
    } else if (provider === Provider.ANTHROPIC && (!model || model.startsWith("gpt"))) {
      model = "claude-3-5-sonnet-20240620";
    }

    const headers: Record<string, string> = {
      "Content-Type": "application/json"
    };

    let body: any;

    if (provider === Provider.ANTHROPIC) {
      headers["x-api-key"] = apiKey;
      headers["anthropic-version"] = "2023-06-01";
      body = {
        model: model || "claude-3-5-sonnet-20240620",
        max_tokens: 4096,
        system: systemPrompt,
        messages: [{ role: "user", content: userMessage }]
      };
    } else {
      if (apiKey) {
        headers["Authorization"] = `Bearer ${apiKey}`;
      }
      body = {
        model: model || "gpt-4o",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userMessage }
        ],
        max_tokens: 4096
      };
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body)
      });

      if (!response.ok) {
        const errorText = await response.text();
        return `⚠️ API Error (${response.status}): ${errorText.slice(0, 300)}`;
      }

      const json: any = await response.json();
      if (provider === Provider.ANTHROPIC) {
        return json.content?.[0]?.text || "No response received.";
      } else {
        return json.choices?.[0]?.message?.content || "No response received.";
      }
    } catch (err: any) {
      return `⚠️ Network Error: ${err.message || err}`;
    }
  }

  // --- Specialized Quick Actions ---

  public static async runAudit(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a Principal Security Auditor & OWASP Fellow.
Audit the provided code for security vulnerabilities, OWASP Top 10 risks, secret leaks, and insecure crypto.
Structure your response with:
1. 📊 Security Scorecard:
   - Security Grade: [A+ / A / B / C / D / F]
   - Risk Level: [LOW / MEDIUM / HIGH / CRITICAL]
   - Exploitability Index: [1-10]
   - OWASP Categories Triggered: [e.g. A01:2021 Broken Access Control, A03:2021 Injection]
2. 🚨 Vulnerability Analysis (line references, attack vectors, CVSS estimate)
3. 🛡️ Secure Remediated Code (Full corrected code block in \`\`\`${lang} ... \`\`\`)`;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runThreatModel(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a Principal Security Architect & Threat Modeling Specialist.
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
3. 🔒 Hardened Architecture & Mitigation Code (Complete runnable fix in \`\`\`${lang} ... \`\`\`)`;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runSupplyChainAudit(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a DevSecOps & Software Supply Chain Security Architect.
Analyze the provided code, imports, dependency declarations, and package references.
Evaluate:
1. 📦 Dependency & Manifest Risks (vulnerable packages, known CVEs, unpinned floating ranges)
2. ⚠️ Typosquatting & Malicious Package Vectors (suspicious library names or risky postinstall hooks)
3. 🔒 Repository Integrity (insecure HTTP endpoints, missing checksums/hashes)
4. 📋 Supply Chain Scorecard & Pinned Safe Manifest Recommendation (Provide pinned manifest or safe import alternatives in \`\`\`${lang} ... \`\`\`)`;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runInputShield(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are an Application Security Engineer specializing in Defensive Design and Input Sanitization.
Analyze the inputs, parameters, and contracts in the provided code.
Produce:
1. 🛡️ Input Boundary Analysis (identify all entry points: REST parameters, queries, body payloads, CLI args, file inputs)
2. 🔒 Defensive Validation Shield (Generate strict schema validation / contract code using the most idiomatic library for ${lang}: e.g. Zod/Joi for TS/JS, Pydantic for Python, Hibernate Validator for Java/Kotlin, serde for Rust)
3. 🧹 Sanitized Type Contracts & Enforced Boundary Assertions in \`\`\`${lang} ... \`\`\``;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runExplain(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a Staff Software Architect.
Provide a clear, high-level and detailed architectural breakdown of this code.
Explain primary flow, data structures, complexity, and potential edge cases.`;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runRefactor(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a Senior Refactoring Specialist.
Refactor this code to maximize readability, maintainability, and clean architecture without altering contracts.
Provide:
1. Key improvements made
2. Complete refactored code block in \`\`\`${lang} ... \`\`\``;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runGenerateTests(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are a QA & Test Automation Architect.
Generate comprehensive unit and edge-case tests (using standard frameworks for ${lang}).
Cover happy paths, edge cases, error conditions, and nullability.
Provide full runnable test code in \`\`\`${lang} ... \`\`\``;
    return this.chat(prompt, code, fileName, lang);
  }

  public static async runSanitize(fileName: string, lang: string, code: string): Promise<string> {
    const prompt = `You are an Automated Security Sanitizer.
1. Strip hardcoded secrets/passwords and replace with env variable access.
2. Upgrade deprecated/weak crypto (MD5, SHA-1 -> SHA-256).
3. Parameterize raw SQL queries.
Provide complete sanitized code in \`\`\`${lang} ... \`\`\``;
    return this.chat(prompt, code, fileName, lang);
  }
}
