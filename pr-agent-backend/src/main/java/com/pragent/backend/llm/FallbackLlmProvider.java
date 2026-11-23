package com.pragent.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the fallback when the primary is rate limited or unreachable.
 * A rejected key is NOT covered: that is a setup mistake the user must see.
 */
public class FallbackLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackLlmProvider.class);

    private final LlmProvider primary;
    private final LlmProvider fallback;

    public FallbackLlmProvider(LlmProvider primary, LlmProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        try {
            return primary.generate(systemPrompt, userPrompt);
        } catch (LlmRateLimitException | LlmUnavailableException e) {
            log.warn("Primary model failed ({}). Using the fallback model.", e.getMessage());
            return fallback.generate(systemPrompt, userPrompt);
        }
    }

    @Override
    public void checkAvailableOrThrow() {
        try {
            primary.checkAvailableOrThrow();
        } catch (LlmUnavailableException e) {
            log.warn("Primary model unavailable ({}). Checking the fallback model.", e.getMessage());
            fallback.checkAvailableOrThrow();
        }
    }
}