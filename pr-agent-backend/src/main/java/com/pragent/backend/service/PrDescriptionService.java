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

            DESCRIPTION: you MUST use exactly this template, with these exact
            headings, in this exact order. Do not merge the sections into one
            paragraph. Do not omit any heading, even if a section is short.

            ## What changed
            <one to three sentences describing the concrete changes>

            ## Why
            <one to two sentences on the motivation, or exactly "Not evident from the diff." if it cannot be determined from the diff alone>
            Do not write vague generic justifications like "improves functionality"
            or "enhances the system" - these are not real reasons. If the diff does
            not explicitly show a bug report, issue reference, or stated goal, the
            reason is not evident and you must say so exactly as instructed.

            ## How to test
            <one to two sentences on how a reviewer could verify this>

            The example below shows the required STRUCTURE only. Do not reuse its
            wording, sentence structure, or phrasing - write fresh content specific
            to the actual diff you are given.

            Example (structure only, do not copy this wording):

            ## What changed
            A new configuration file was added enabling caching for database queries.

            ## Why
            Not evident from the diff.

            ## How to test
            Run the existing integration test suite and confirm query response
            times decrease under repeated identical queries.
            """;

    private final ChatClient chatClient;

    public PrDescriptionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PrDescription generate(String diff) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Here is the diff:\n\n" + diff
                        + "\n\nRemember: the description must use the ## What changed / "
                        + "## Why / ## How to test template exactly.")
                .call()
                .entity(PrDescription.class);
    }
}
