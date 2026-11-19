package com.pragent.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmPropertiesTest {

    @Test
    void blankProviderDefaultsToOllamaAndNeedsNoKey() {
        LlmProperties p = new LlmProperties("", null, null, null);

        assertTrue(p.isOllama());
        assertEquals("ollama", p.provider());
    }

    @Test
    void geminiGetsPresetUrlAndModel() {
        LlmProperties p = new LlmProperties("gemini", "k", "", "");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", p.baseUrl());
        assertEquals("gemini-2.5-flash", p.model());
    }

    @Test
    void explicitValuesBeatThePresetAndTrailingSlashIsRemoved() {
        LlmProperties p = new LlmProperties("GROQ", " k ", "https://example.test/v1/", "my-model");

        assertEquals("groq", p.provider());
        assertEquals("k", p.apiKey());
        assertEquals("https://example.test/v1", p.baseUrl());
        assertEquals("my-model", p.model());
    }

    @Test
    void cloudProviderWithoutKeyFailsAndNamesTheVariable() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LlmProperties("gemini", "  ", null, null));

        assertTrue(e.getMessage().contains("LLM_API_KEY"));
    }

    @Test
    void unknownProviderListsTheValidOnes() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LlmProperties("skynet", "k", null, null));

        assertTrue(e.getMessage().contains("gemini"));
    }

    @Test
    void customProviderNeedsUrlAndModel() {
        assertThrows(IllegalStateException.class, () -> new LlmProperties("custom", "k", "", ""));
        assertThrows(IllegalStateException.class, () -> new LlmProperties("custom", "k", "https://x.test", ""));
        assertDoesNotThrow(() -> new LlmProperties("custom", "k", "https://x.test", "m"));
    }

    @Test
    void toStringNeverShowsTheKey() {
        String text = new LlmProperties("gemini", "super-secret-key", null, null).toString();

        assertFalse(text.contains("super-secret-key"));
        assertTrue(text.contains("****"));
    }
}