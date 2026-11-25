package com.pragent.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackLlmProviderTest {

    @Test
    void usesThePrimaryWhenItWorks() {
        ScriptedProvider primary = new ScriptedProvider().thenReturn("from primary");
        ScriptedProvider fallback = new ScriptedProvider().thenReturn("from fallback");

        assertEquals("from primary", new FallbackLlmProvider(primary, fallback).generate("s", "u"));
        assertEquals(0, fallback.generateCalls);
    }

    @Test
    void fallsBackOnRateLimit() {
        ScriptedProvider primary = new ScriptedProvider().thenThrow(new LlmRateLimitException("429", null));
        ScriptedProvider fallback = new ScriptedProvider().thenReturn("from fallback");

        assertEquals("from fallback", new FallbackLlmProvider(primary, fallback).generate("s", "u"));
    }

    @Test
    void fallsBackWhenUnavailable() {
        ScriptedProvider primary = new ScriptedProvider().thenThrow(new LlmUnavailableException("down"));
        ScriptedProvider fallback = new ScriptedProvider().thenReturn("from fallback");

        assertEquals("from fallback", new FallbackLlmProvider(primary, fallback).generate("s", "u"));
    }

    @Test
    void doesNotHideABadKey() {
        ScriptedProvider primary = new ScriptedProvider().thenThrow(new LlmAuthException("bad key"));
        ScriptedProvider fallback = new ScriptedProvider().thenReturn("from fallback");

        assertThrows(LlmAuthException.class, () -> new FallbackLlmProvider(primary, fallback).generate("s", "u"));
        assertEquals(0, fallback.generateCalls);
    }

    @Test
    void availabilityCheckUsesTheFallbackWhenThePrimaryIsDown() {
        ScriptedProvider primary = new ScriptedProvider();
        primary.checkFailure = new LlmUnavailableException("down");
        ScriptedProvider fallback = new ScriptedProvider();

        assertDoesNotThrow(() -> new FallbackLlmProvider(primary, fallback).checkAvailableOrThrow());
        assertEquals(1, fallback.checkCalls);
    }

    @Test
    void availabilityCheckFailsWhenBothAreDown() {
        ScriptedProvider primary = new ScriptedProvider();
        primary.checkFailure = new LlmUnavailableException("primary down");
        ScriptedProvider fallback = new ScriptedProvider();
        fallback.checkFailure = new LlmUnavailableException("fallback down");

        assertThrows(LlmUnavailableException.class,
                () -> new FallbackLlmProvider(primary, fallback).checkAvailableOrThrow());
    }
}