package com.pragent.cli;

import com.pragent.cli.config.CliConfig;
import com.pragent.cli.diff.DiffChunker;
import com.pragent.cli.diff.DiffFilter;
import com.pragent.cli.diff.NumstatParser;
import com.pragent.cli.git.GitClient;
import com.pragent.cli.http.BackendClient;
import com.pragent.cli.http.UpdatePrResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Command(
        name = "pr-agent",
        mixinStandardHelpOptions = true,
        version = "pr-agent-cli 0.6.0",
        description = "Generates a PR title/description from a git diff and updates the PR on GitHub."
)
public class Main implements Runnable {

    @Option(names = {"--base"},
            description = "Base branch/ref to diff against, e.g. main. Falls back to default.base in .pragent.properties if not given.")
    private String base;

    @Option(names = {"--repo-path"}, defaultValue = ".",
            description = "Path to the git repository. Defaults to the current directory.")
    private String repoPath;

    @Option(names = {"--max-chunk-chars"},
            description = "Max characters per chunk. Falls back to max.chunk.chars in .pragent.properties, then 8000.")
    private Integer maxChunkChars;

    @Option(names = {"--backend-url"},
            description = "Base URL of the pr-agent backend. Falls back to backend.url in .pragent.properties, then http://localhost:8080.")
    private String backendUrl;

    @Option(names = {"--dry-run"},
            description = "Print the filtered diff to stdout instead of calling the backend. No network calls.")
    private boolean dryRun;

    @Override
    public void run() {
        try {
            Path repoDir = Path.of(repoPath).toAbsolutePath().normalize();
            GitClient git = GitClient.forRepoContaining(repoDir);
            CliConfig config = CliConfig.loadFrom(git.getRepoRoot());

            String effectiveBase = resolveBase(config);
            String effectiveBackendUrl = backendUrl != null ? backendUrl
                    : config.backendUrl().orElse("http://localhost:8080");
            int effectiveMaxChunkChars = maxChunkChars != null ? maxChunkChars
                    : config.maxChunkChars().orElse(8000);

            List<String> chunks = extractDiffChunks(git, effectiveBase, effectiveMaxChunkChars);
            if (chunks == null) {
                return;
            }

            if (dryRun) {
                printChunks(chunks);
            } else {
                callBackendAndUpdatePr(git, chunks, effectiveBackendUrl);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private String resolveBase(CliConfig config) {
        if (base != null) {
            return base;
        }
        return config.defaultBase().orElseThrow(() -> new IllegalArgumentException(
                "--base is required (pass it directly, or set default.base in .pragent.properties)."));
    }

    private List<String> extractDiffChunks(GitClient git, String base, int maxChunkChars) throws Exception {
        DiffFilter filter = new DiffFilter();
        DiffChunker chunker = new DiffChunker();

        String numstat = git.run(List.of("diff", "--numstat", base + "...HEAD"));
        List<DiffFilter.FileChange> allChanges = NumstatParser.parse(numstat);

        if (allChanges.isEmpty()) {
            System.err.println("No changes found against " + base + ". Nothing to do.");
            return null;
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
            return null;
        }

        List<String> diffArgs = new ArrayList<>(List.of("diff", base + "...HEAD", "--"));
        for (DiffFilter.FileChange change : kept) {
            diffArgs.add(change.path());
        }
        String fullDiff = git.run(diffArgs);

        List<String> chunks = chunker.chunk(fullDiff, maxChunkChars);
        System.err.println("Diff split into " + chunks.size() + " chunk(s).");
        return chunks;
    }

    private void printChunks(List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.size() > 1) {
                System.out.println("===== CHUNK " + (i + 1) + "/" + chunks.size() + " =====");
            }
            System.out.println(chunks.get(i));
        }
    }

    private void callBackendAndUpdatePr(GitClient git, List<String> chunks, String backendUrl) throws Exception {
        String[] ownerAndRepo = git.getRemoteOwnerAndRepo();
        String owner = ownerAndRepo[0];
        String repo = ownerAndRepo[1];
        String branch = git.getCurrentBranch();

        System.err.println("Updating PR for " + owner + "/" + repo + " branch " + branch
                + " via " + backendUrl + " ...");

        BackendClient backendClient = new BackendClient(backendUrl);
        UpdatePrResponse response = backendClient.updatePr(owner, repo, branch, chunks);

        System.out.println("Updated PR #" + response.prNumber() + ": " + response.prUrl());
        System.out.println("Title: " + response.generated().title());
        System.out.println("Type: " + response.generated().type());
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}