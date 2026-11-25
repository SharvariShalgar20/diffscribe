package com.pragent.backend.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetryingLlmProviderTest {

    private final List<Duration> sleeps = new ArrayList<>();

    private RetryingLlmProvider retrying(ScriptedProvider p, int attempts) {
        return new RetryingLlmProvider(p, attempts, Duration.ofSeconds(20), sleeps::add);
    }

    @Test
    void returnsAtOnceOnSuccess() {
        ScriptedProvider p = new ScriptedProvider().thenReturn("ok");

        assertEquals("ok", retrying(p, 3).generate("s", "u"));
        assertEquals(1, p.generateCalls);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void retriesARateLimitAndWaitsForRetryAfter() {
        ScriptedProvider p = new ScriptedProvider()
                .thenThrow(new LlmRateLimitException("slow down", Duration.ofSeconds(5)))
                .thenReturn("ok");

        assertEquals("ok", retrying(p, 3).generate("s", "u"));
        assertEquals(2, p.generateCalls);
        assertEquals(List.of(Duration.ofSeconds(5)), sleeps);
    }

    @Test
    void backoffDoublesWhenNoRetryAfterIsGiven() {
        ScriptedProvider p = new ScriptedProvider()
                .thenThrow(new LlmUnavailableException("down"))
                .thenThrow(new LlmUnavailableException("down"))
                .thenReturn("ok");

        assertEquals("ok", retrying(p, 3).generate("s", "u"));
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), sleeps);
    }

    @Test
    void givesUpAfterMaxAttemptsAndRethrows() {
        ScriptedProvider p = new ScriptedProvider()
                .thenThrow(new LlmRateLimitException("1", null))
                .thenThrow(new LlmRateLimitException("2", null))
                .thenThrow(new LlmRateLimitException("3", null));

        LlmRateLimitException e = assertThrows(LlmRateLimitException.class,
                () -> retrying(p, 3).generate("s", "u"));

        assertEquals("3", e.getMessage());
        assertEquals(3, p.generateCalls);
        assertEquals(2, sleeps.size());
    }

    @Test
    void neverRetriesAuthErrors() {
        ScriptedProvider p = new ScriptedProvider().thenThrow(new LlmAuthException("bad key"));

        assertThrows(LlmAuthException.class, () -> retrying(p, 3).generate("s", "u"));
        assertEquals(1, p.generateCalls);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void failsFastWhenRetryAfterIsLongerThanTheMaxWait() {
        ScriptedProvider p = new ScriptedProvider()
                .thenThrow(new LlmRateLimitException("daily limit", Duration.ofSeconds(120)));

        assertThrows(LlmRateLimitException.class, () -> retrying(p, 3).generate("s", "u"));
        assertEquals(1, p.generateCalls);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void availabilityCheckIsNotRetried() {
        ScriptedProvider p = new ScriptedProvider();
        p.checkFailure = new LlmUnavailableException("down");

        assertThrows(LlmUnavailableException.class, () -> retrying(p, 3).checkAvailableOrThrow());
        assertEquals(1, p.checkCalls);
    }
}