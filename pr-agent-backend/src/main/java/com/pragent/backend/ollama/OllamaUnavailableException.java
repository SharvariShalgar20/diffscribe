package com.pragent.backend.ollama;

public class OllamaUnavailableException extends RuntimeException {
    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}