package com.pragent.backend.service;

import com.pragent.backend.dto.PrDescription;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Owns the actual prompt-building and LLM call. Kept separate from
 * PrGenerationController so the controller stays a thin HTTP layer, and so
 * Phase 3's prompt iteration has one focused place to happen - testable
 * with a mocked ChatClient, no Spring web context needed.
 */
@Service
public class PrDescriptionService {
    private static final String SYSTEM_PROMPT = """
            You are an assistant that writes pull request titles and descriptions
            from a git diff. Base everything strictly on the diff content - do not
            invent changes that are not shown in the diff. Write the title in
            conventional-commit style (type: short summary). Keep the description
            factual and concise.
            """;

    private final ChatClient chatClient;

    public PrDescriptionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PrDescription generate(String diff) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Here is the diff:\n\n" + diff)
                .call()
                .entity(PrDescription.class);
    }
}
