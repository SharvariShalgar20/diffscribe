package com.pragent.backend.llm;

import com.pragent.backend.ollama.OllamaHealthChecker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** Local Ollama model, reached through Spring AI's ChatClient. */
@Component
public class OllamaProvider implements LlmProvider {

    private final ChatClient chatClient;
    private final OllamaHealthChecker healthChecker;

    public OllamaProvider(ChatClient chatClient, OllamaHealthChecker healthChecker) {
        this.chatClient = chatClient;
        this.healthChecker = healthChecker;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        var request = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request = request.system(systemPrompt);
        }
        return request.user(userPrompt).call().content();
    }

    @Override
    public void checkAvailableOrThrow() {
        healthChecker.checkReachableOrThrow();
    }
}