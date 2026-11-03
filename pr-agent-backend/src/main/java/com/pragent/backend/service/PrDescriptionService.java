package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.ollama.OllamaHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PrDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrDescriptionService.class);

    private static final int MAX_ATTEMPTS = 2;

    private static final String OUTPUT_RULES = """
            Reply with EXACTLY these five labeled lines and nothing else. No JSON,
            no markdown, no headings, no code fences, no text before or after.

            TYPE: <one of: feat, fix, refactor, docs, test, chore>
            TITLE: <short summary, lowercase, imperative mood, no type prefix, max 60 characters>
            WHAT: <one to three sentences describing the concrete changes>
            WHY: <one to two sentences on the motivation, or exactly: Not evident from the diff.>
            TEST: <one to two sentences on how a reviewer could verify this>

            TYPE definitions - choose exactly one:
            - feat: adds new functionality or a capability that did not exist before
            - fix: corrects a bug in existing behavior
            - refactor: restructures existing code without changing its external behavior
            - docs: changes only documentation or comments
            - test: adds or changes tests only
            - chore: build config, tooling, or dependency changes with no source behavior impact

            A change that only adds new files or new methods is "feat", not "fix".
            Adding validation or guards for a case that was never handled before is
            "feat". "fix" needs evidence that existing behavior was wrong and is now
            corrected. Moving code around without changing what it does is "refactor".

            Do not write vague justifications like "improves functionality" in WHY.
            If the diff does not state a goal, write exactly: Not evident from the diff.

            Example 1 (structure only, do not copy the wording):
            TYPE: feat
            TITLE: enable caching for database queries
            WHAT: A new configuration file turns on caching for database queries.
            WHY: Not evident from the diff.
            TEST: Run the integration tests and confirm repeated identical queries get faster.

            Example 2:
            TYPE: chore
            TITLE: bump jackson-databind to 2.17.2
            WHAT: Updates the jackson-databind version in pom.xml.
            WHY: Not evident from the diff.
            TEST: Build the project and run the existing test suite.
            """;

    private static final String SINGLE_DIFF_SYSTEM_PROMPT = """
            You are an assistant that writes pull request text from a git diff.
            Base everything strictly on the diff. Do not invent changes, reasons,
            or context that are not shown in the diff.

            """ + OUTPUT_RULES;

    private static final String CHUNK_SUMMARY_PROMPT = """
            You are summarizing ONE part of a larger git diff that was split into
            multiple pieces because of its size. Summarize only the concrete
            changes shown in THIS piece, in 2 to 4 plain-text sentences. Do not
            guess about content in parts you cannot see. Do not write a title,
            type, or any headings - plain text only.
            """;

    private static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are writing pull request text by combining summaries of different
            parts of ONE larger diff that was split into pieces due to size. Treat
            all summaries together as describing a single overall change - do not
            write "part 1 does X, part 2 does Y". Base everything strictly on the
            summaries.

            """ + OUTPUT_RULES;

    private static final String REMINDER =
            "\n\nReply with the five labeled lines only: TYPE, TITLE, WHAT, WHY, TEST.";

    private static final String RETRY_HINT =
            "\n\nYour previous reply could not be read. Answer again with ONLY the five lines "
                    + "TYPE:, TITLE:, WHAT:, WHY:, TEST: and nothing else.";

    private final ChatClient chatClient;
    private final OllamaHealthChecker ollamaHealthChecker;

    public PrDescriptionService(ChatClient chatClient, OllamaHealthChecker ollamaHealthChecker) {
        this.chatClient = chatClient;
        this.ollamaHealthChecker = ollamaHealthChecker;
    }

    public PrDescription generate(List<String> diffChunks) {
        ollamaHealthChecker.checkReachableOrThrow();

        ModelOutputParser.Parsed parsed;
        if (diffChunks.size() == 1) {
            parsed = askModel(SINGLE_DIFF_SYSTEM_PROMPT,
                    "Here is the diff:\n\n" + diffChunks.get(0) + REMINDER);
        } else {
            parsed = askModel(SYNTHESIS_SYSTEM_PROMPT,
                    buildSynthesisInput(summarizeChunks(diffChunks)));
        }

        ChangeType type = parsed.type();
        Optional<ChangeType> ruled = TypeRules.fromPaths(TypeRules.changedPaths(diffChunks));
        if (ruled.isPresent() && ruled.get() != type) {
            log.info("Type rule overrides the model: {} -> {}", type, ruled.get());
            type = ruled.get();
        }

        String description = ModelOutputParser.formatDescription(parsed)
                + RiskFlags.render(RiskFlags.notes(diffChunks));

        return new PrDescription(
                ModelOutputParser.formatTitle(type, parsed.summary()),
                description,
                type);
    }

    private ModelOutputParser.Parsed askModel(String systemPrompt, String userPrompt) {
        ModelOutputException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = attempt == 1 ? userPrompt : userPrompt + RETRY_HINT;
            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
            try {
                return ModelOutputParser.parse(raw);
            } catch (ModelOutputException e) {
                last = e;
                log.warn("Model answer unusable (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw last;
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
            summaries.add(summary == null ? "" : summary);
        }
        return summaries;
    }

    private String buildSynthesisInput(List<String> summaries) {
        StringBuilder combined = new StringBuilder("Here are the part summaries:\n\n");
        for (int i = 0; i < summaries.size(); i++) {
            combined.append("Summary of part ").append(i + 1).append(":\n")
                    .append(summaries.get(i)).append("\n\n");
        }
        return combined + "Describe the change as one coherent whole." + REMINDER;
    }
}