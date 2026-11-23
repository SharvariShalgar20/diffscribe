package com.pragent.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Retries transient failures (rate limit, unavailable) with backoff.
 * Never retries auth errors. If the wait would exceed maxWait, it gives up
 * at once so a git push is not blocked for minutes.
 */
public class RetryingLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(RetryingLlmProvider.class);

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final LlmProvider delegate;
    private final int maxAttempts;
    private final Duration maxWait;
    private final Sleeper sleeper;

    public RetryingLlmProvider(LlmProvider delegate, int maxAttempts, Duration maxWait) {
        this(delegate, maxAttempts, maxWait, d -> Thread.sleep(d.toMillis()));
    }

    public RetryingLlmProvider(LlmProvider delegate, int maxAttempts, Duration maxWait, Sleeper sleeper) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.maxWait = maxWait;
        this.sleeper = sleeper;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.generate(systemPrompt, userPrompt);
            } catch (LlmRateLimitException | LlmUnavailableException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                Duration wait = waitFor(e, attempt);
                if (wait.compareTo(maxWait) > 0) {
                    log.warn("Not retrying: the provider asks for a {}s wait, longer than the {}s limit.",
                            wait.toSeconds(), maxWait.toSeconds());
                    throw e;
                }
                log.warn("Model call failed (attempt {}/{}): {}. Retrying in {}s.",
                        attempt, maxAttempts, e.getMessage(), wait.toSeconds());
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Pre-flight checks are not retried: failing fast is their whole purpose. */
    @Override
    public void checkAvailableOrThrow() {
        delegate.checkAvailableOrThrow();
    }

    private Duration waitFor(RuntimeException e, int attempt) {
        if (e instanceof LlmRateLimitException rate && rate.retryAfter().isPresent()) {
            return rate.retryAfter().get();
        }
        Duration backoff = Duration.ofSeconds(2L << (attempt - 1)); // 2s, 4s, 8s, ...
        return backoff.compareTo(maxWait) > 0 ? maxWait : backoff;
    }
}