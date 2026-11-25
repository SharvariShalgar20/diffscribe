package com.pragent.backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

/** Optional second provider. Disabled while 'provider' is blank. */
@ConfigurationProperties(prefix = "pragent.llm.fallback")
public record LlmFallbackProperties(String provider, String apiKey, String baseUrl, String model) {

    /** Same validation as the primary, so a bad fallback config stops startup too. */
    public Optional<LlmProperties> toLlmProperties() {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new LlmProperties(provider, apiKey, baseUrl, model));
    }

    @Override
    public String toString() {
        return "LlmFallbackProperties[provider=" + provider + ", apiKey="
                + (apiKey == null || apiKey.isBlank() ? "<none>" : "****") + "]";
    }
}