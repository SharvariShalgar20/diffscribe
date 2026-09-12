package com.pragent.backend.service;

import com.pragent.backend.dto.PrDescription;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the actual prompt-building and LLM call. Kept separate from
 * PrGenerationController so the controller stays a thin HTTP layer, and so
 * Phase 3's prompt iteration has one focused place to happen - testable
 * with a mocked ChatClient, no Spring web context needed.
 */
@Service
public class PrDescriptionService {

    private static final String OUTPUT_RULES = """
            TITLE: conventional-commit style, "type: short summary", lowercase
            type, imperative mood, under ~72 characters.

            TYPE must reflect the actual nature of the change - choose exactly one:
            - feat: adds new functionality or a capability that did not exist before
            - fix: corrects a bug in existing behavior
            - refactor: restructures existing code without changing its external behavior
            - docs: changes only documentation or comments
            - test: adds or changes tests only
            - chore: build config, tooling, or dependency changes with no source behavior impact

            A change that only adds new files or new methods is "feat", not "fix" -
            "fix" requires that something was previously broken and is now corrected.
            
            Adding validation, checks, or guards for a case that was never previously handled is "feat" (a new capability), NOT "fix" - "fix" only applies when existing behavior was incorrect and is now corrected. Example: adding a null-check to a method that never had one before is "feat: add input validation", not "fix: ...", unless the diff shows evidence a bug was actually occurring beforehand

            DESCRIPTION: you MUST use exactly this template, with these exact
            headings, in this exact order. Do not merge the sections into one
            paragraph. Do not omit any heading, even if a section is short.

            ## What changed
            <one to three sentences describing the concrete changes>

            ## Why
            <one to two sentences on the motivation, or exactly "Not evident from the diff." if it cannot be determined>
            Do not write vague generic justifications like "improves functionality"
            or "enhances the system" - these are not real reasons.

            ## How to test
            <one to two sentences on how a reviewer could verify this>
            """;

    private static final String FORMAT_EXAMPLE = """
            Example (structure only, do not copy this wording):

            ## What changed
            A new configuration file was added enabling caching for database queries.

            ## Why
            Not evident from the diff.

            ## How to test
            Run the existing integration test suite and confirm query response
            times decrease under repeated identical queries.
            """;

    private static final String SINGLE_DIFF_SYSTEM_PROMPT = """
            You are an assistant that writes pull request titles and descriptions
            from a git diff.

            Base everything strictly on the diff content. Do not invent changes,
            reasons, or context that are not shown in the diff.

            """ + OUTPUT_RULES + FORMAT_EXAMPLE;

    private static final String CHUNK_SUMMARY_PROMPT = """
            You are summarizing ONE part of a larger git diff that was split into
            multiple pieces because of its size. Summarize only the concrete
            changes shown in THIS piece, in 2 to 4 plain-text sentences. Do not
            guess about content in parts you cannot see. Do not write a title,
            type, or any markdown headings - plain text only.
            """;

    private static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are writing a single pull request title and description by
            combining summaries of different parts of ONE larger diff that was
            split into pieces due to size. Treat all summaries together as
            describing a single overall change - do not write "part 1 does X,
            part 2 does Y" style. Synthesize into one coherent result.

            """ + OUTPUT_RULES + FORMAT_EXAMPLE;

    private final ChatClient chatClient;

    public PrDescriptionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PrDescription generate(List<String> diffChunks) {
        if (diffChunks.size() == 1) {
            return generateFromSingleDiff(diffChunks.get(0));
        }
        List<String> summaries = summarizeChunks(diffChunks);
        return synthesizeFromSummaries(summaries);
    }

    private PrDescription generateFromSingleDiff(String diff) {
        return chatClient.prompt()
                .system(SINGLE_DIFF_SYSTEM_PROMPT)
                .user("Here is the diff:\n\n" + diff
                        + "\n\nRemember: the description must use the ## What changed / "
                        + "## Why / ## How to test template exactly.")
                .call()
                .entity(PrDescription.class);
    }

    private List<String> summarizeChunks(List<String> diffChunks) {
        List<String> summaries = new ArrayList<>();
        int total = diffChunks.size();
        for (int i = 0; i < total; i++) {
            String summary = chatClient.prompt()
                    .system(CHUNK_SUMMARY_PROMPT)
                    .user("This is part " + (i + 1) + " of " + total + " of a single diff:\n\n" + diffChunks.get(i))
                    .call()
                    .content();
            summaries.add(summary);
        }
        return summaries;
    }

    private PrDescription synthesizeFromSummaries(List<String> summaries) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < summaries.size(); i++) {
            combined.append("Summary of part ").append(i + 1).append(":\n")
                    .append(summaries.get(i)).append("\n\n");
        }

        return chatClient.prompt()
                .system(SYNTHESIS_SYSTEM_PROMPT)
                .user("Here are the part summaries:\n\n" + combined
                        + "\nRemember: the description must use the ## What changed / "
                        + "## Why / ## How to test template exactly, and describe the "
                        + "change as one coherent whole.")
                .call()
                .entity(PrDescription.class);
    }
}
