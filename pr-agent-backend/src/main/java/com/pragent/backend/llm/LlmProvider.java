package com.pragent.backend.llm;

/**
 * The only thing the rest of the backend knows about a language model.
 * Implementations decide how to reach one (local Ollama, a hosted API, ...).
 */
public interface LlmProvider {

    /**
     * Sends one prompt and returns the model's raw text answer.
     *
     * @param systemPrompt instructions for the model; may be blank
     * @param userPrompt   the actual input
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * Fast pre-flight check. Throws a runtime exception with an actionable
     * message if the model cannot be reached, so callers fail in seconds
     * instead of waiting for a long timeout.
     */
    void checkAvailableOrThrow();
}