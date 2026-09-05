package com.pragent.backend;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final ChatClient chatClient;

    public HealthController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/api/test-llm")
    public String testLlm() {
        return chatClient.prompt()
                .user("Reply with exactly the words: Ollama connection working.")
                .call()
                .content();
    }
}
