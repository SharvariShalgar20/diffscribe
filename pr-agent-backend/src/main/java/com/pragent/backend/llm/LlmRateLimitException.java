package com.pragent.backend.llm;

import java.time.Duration;
import java.util.Optional;

/** The provider answered 429. Carries the Retry-After hint when the provider sent one. */
public class LlmRateLimitException extends RuntimeException {

    private final Duration retryAfter;

    public LlmRateLimitException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}