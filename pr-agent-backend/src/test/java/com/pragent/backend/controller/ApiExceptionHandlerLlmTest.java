package com.pragent.backend.controller;

import com.pragent.backend.llm.LlmAuthException;
import com.pragent.backend.llm.LlmRateLimitException;
import com.pragent.backend.llm.LlmUnavailableException;
import com.pragent.backend.ollama.OllamaUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerLlmTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void badKeyIs502() {
        ResponseEntity<String> r = handler.handleLlmAuth(new LlmAuthException("bad key"));
        assertEquals(502, r.getStatusCode().value());
        assertEquals("bad key", r.getBody());
    }

    @Test
    void rateLimitIs429() {
        ResponseEntity<String> r = handler.handleLlmRateLimit(new LlmRateLimitException("slow", Duration.ofSeconds(3)));
        assertEquals(429, r.getStatusCode().value());
    }

    @Test
    void unavailableIs503() {
        ResponseEntity<String> r = handler.handleLlmUnavailable(new LlmUnavailableException("down"));
        assertEquals(503, r.getStatusCode().value());
    }

    @Test
    void ollamaDownIsStillAnLlmUnavailableException() {
        // retry and fallback key off the general type, so Ollama must be one
        assertEquals(true, new OllamaUnavailableException("x", null) instanceof LlmUnavailableException);
    }
}