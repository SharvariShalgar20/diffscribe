package com.pragent.backend.llm;

/** The provider rejected the API key (401/403). Retrying cannot help. */
public class LlmAuthException extends RuntimeException {
    public LlmAuthException(String message) {
        super(message);
    }
}