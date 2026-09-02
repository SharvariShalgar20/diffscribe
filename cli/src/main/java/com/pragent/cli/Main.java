package com.pragent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 1: extracts a clean, filtered diff and prints it to stdout.
 *
 * No LLM call yet - Phase 2 will take this output and send it to the
 * Spring AI backend. This command's only job is to prove the input is clean.
 */
@Command(
        name = "pr-agent",
        mixinStandardHelpOptions = true,
        version = "pr-agent-cli 0.2.0",
        description = "Extracts a clean, filtered git diff, ready to hand to an LLM."
)
public class Main implements Runnable {

    @Option(names = {"--base"}, required = true,
            description = "Base branch/ref to diff against, e.g. main or origin/main.")
    private String base;

    @Option(names = {"--repo-path"}, defaultValue = ".",
            description = "Path to the git repository. Defaults to the current directory.")
    private String repoPath;

    @Option(names = {"--max-chunk-chars"}, defaultValue = "8000",
            description = "Max characters per chunk when the diff is split for the LLM.")
    private int maxChunkChars;

    @Override
    public void run() {
        Path repoDir = Path.of(repoPath).toAbsolutePath().normalize();
        DiffFilter filter = new DiffFilter();
        DiffChunker chunker = new DiffChunker();

        try {
            GitClient git = GitClient.forRepoContaining(repoDir);
            String numstat = git.run(List.of("diff", "--numstat", base + "...HEAD"));
            List<DiffFilter.FileChange> allChanges = NumstatParser.parse(numstat);

            if (allChanges.isEmpty()) {
                System.err.println("No changes found against " + base + ". Nothing to do.");
                return;
            }

            List<DiffFilter.FileChange> kept = filter.keep(allChanges);
            List<DiffFilter.FileChange> filtered = allChanges.stream()
                    .filter(c -> !kept.contains(c))
                    .collect(Collectors.toList());

            System.err.println("Changed files: " + allChanges.size()
                    + " | kept: " + kept.size()
                    + " | filtered as noise: " + filtered.size());
            if (!filtered.isEmpty()) {
                System.err.println("Filtered: " + filtered.stream()
                        .map(DiffFilter.FileChange::path)
                        .collect(Collectors.joining(", ")));
            }

            if (kept.isEmpty()) {
                System.err.println("All changed files were filtered as noise. Nothing to send.");
                return;
            }

            List<String> diffArgs = new ArrayList<>(List.of("diff", base + "...HEAD", "--"));
            for (DiffFilter.FileChange change : kept) {
                diffArgs.add(change.path());
            }
            String fullDiff = git.run(diffArgs);

            List<String> chunks = chunker.chunk(fullDiff, maxChunkChars);
            System.err.println("Diff split into " + chunks.size() + " chunk(s).");

            for (int i = 0; i < chunks.size(); i++) {
                if (chunks.size() > 1) {
                    System.out.println("===== CHUNK " + (i + 1) + "/" + chunks.size() + " =====");
                }
                System.out.println(chunks.get(i));
            }

        } catch (Exception e) {
            System.err.println("Error extracting diff: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
