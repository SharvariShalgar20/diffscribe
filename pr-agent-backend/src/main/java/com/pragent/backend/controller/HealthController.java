package com.pragent.backend.controller;

import com.pragent.backend.llm.LlmProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final LlmProvider llmProvider;

    public HealthController(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/api/test-llm")
    public String testLlm() {
        return llmProvider.generate("", "Reply with exactly the words: Ollama connection working.");
    }
}
