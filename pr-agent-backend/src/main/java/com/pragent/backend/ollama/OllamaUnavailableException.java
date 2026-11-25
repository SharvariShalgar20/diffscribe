package com.pragent.backend.ollama;

import com.pragent.backend.llm.LlmUnavailableException;

public class OllamaUnavailableException extends LlmUnavailableException {
    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}