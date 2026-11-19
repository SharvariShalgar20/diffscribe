package com.pragent.backend.llm;

import java.util.Arrays;
import java.util.Optional;

/** Known OpenAI-compatible hosts. "custom" has no defaults, so URL and model are required. */
public enum ProviderPreset {
    GEMINI("gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
    GROQ("groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    OPENROUTER("openrouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
    CUSTOM("custom", "", "");

    private final String id;
    private final String defaultBaseUrl;
    private final String defaultModel;

    ProviderPreset(String id, String defaultBaseUrl, String defaultModel) {
        this.id = id;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String id() { return id; }
    public String defaultBaseUrl() { return defaultBaseUrl; }
    public String defaultModel() { return defaultModel; }

    public static Optional<ProviderPreset> find(String id) {
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }

    public static String knownIds() {
        return "ollama, " + String.join(", ", Arrays.stream(values()).map(ProviderPreset::id).toList());
    }
}