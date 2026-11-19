package com.pragent.backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "pragent.llm")
public record LlmProperties(String provider, String apiKey, String baseUrl, String model) {

    public static final String OLLAMA = "ollama";

    public LlmProperties {
        provider = isBlank(provider) ? OLLAMA : provider.strip().toLowerCase(Locale.ROOT);
        apiKey = isBlank(apiKey) ? "" : apiKey.strip();
        baseUrl = isBlank(baseUrl) ? "" : baseUrl.strip().replaceAll("/+$", "");
        model = isBlank(model) ? "" : model.strip();

        if (!OLLAMA.equals(provider)) {
            ProviderPreset preset = ProviderPreset.find(provider).orElse(null);
            if (preset == null) {
                throw new IllegalStateException("Unknown LLM_PROVIDER '" + provider
                        + "'. Use one of: " + ProviderPreset.knownIds() + ".");
            }
            if (apiKey.isEmpty()) {
                throw new IllegalStateException("LLM_API_KEY is not set for provider '" + provider
                        + "'. Set it as an environment variable (never in application.yaml) "
                        + "and restart the backend.");
            }
            if (baseUrl.isEmpty()) {
                baseUrl = preset.defaultBaseUrl();
            }
            if (model.isEmpty()) {
                model = preset.defaultModel();
            }
            if (baseUrl.isEmpty() || model.isEmpty()) {
                throw new IllegalStateException("Provider '" + provider
                        + "' needs both LLM_BASE_URL and LLM_MODEL.");
            }
        }
    }

    public boolean isOllama() {
        return OLLAMA.equals(provider);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "LlmProperties[provider=" + provider + ", apiKey=" + (apiKey.isEmpty() ? "<none>" : "****")
                + ", baseUrl=" + baseUrl + ", model=" + model + "]";
    }
}