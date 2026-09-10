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
            from a git diff.

            Base everything strictly on the diff content. Do not invent changes,
            reasons, or context that are not shown in the diff.

            TITLE: conventional-commit style, "type: short summary", lowercase
            type, imperative mood, under ~72 characters.

            TYPE must reflect the actual nature of the change - choose exactly one:
            - feat: adds new functionality or a capability that did not exist before
            - fix: corrects a bug in existing behavior
            - refactor: restructures existing code without changing its external behavior
            - docs: changes only documentation or comments
            - test: adds or changes tests only
            - chore: build config, tooling, or dependency changes with no source behavior impact

            A diff that only adds new files or new methods is "feat", not "fix" -
            "fix" requires that something was previously broken and is now corrected.

            DESCRIPTION: markdown formatted with exactly these three sections,
            in this order, using these exact headings:
            ## What changed
            ## Why
            ## How to test

            If the reason for the change is not evident from the diff itself,
            state that explicitly under "Why" (e.g. "Not evident from the diff.")
            rather than guessing or inventing motivation. If the change appears to
            be a refactor with no behavior change, say so explicitly under
            "What changed".
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
