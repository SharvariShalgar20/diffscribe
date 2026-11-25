package com.pragent.backend.llm;

/** The model could not be reached: timeout, network error, provider 5xx, or Ollama down. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}