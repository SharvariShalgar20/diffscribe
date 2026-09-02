package com.pragent.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around shelling out to the system `git` binary.
 *
 * We shell out rather than use a pure-Java git library (e.g. JGit) so that
 * the diff we extract is exactly what your actual git config would produce -
 * same diff algorithm, same .gitattributes handling. The tradeoff is this
 * requires `git` to be installed and on PATH, which is a safe assumption for
 * a developer tool.
 */
public class GitClient {

    private final Path repoDir;

    public GitClient(Path repoDir) {
        this.repoDir = repoDir;
    }

    /**
     * Resolves the actual top-level directory of the git repo containing
     * {@code startDir}, and returns a GitClient rooted there.
     *
     * This matters because `git diff --numstat` always reports paths
     * relative to the repo root, regardless of cwd - but a later
     * `git diff -- <path>` interprets pathspecs relative to cwd. If we ran
     * commands from an arbitrary subdirectory (e.g. this CLI invoked from
     * cli/ inside a larger repo), root-relative paths from numstat wouldn't
     * match anything from that subdirectory, and the diff would silently
     * come back empty. Always operating from the resolved root avoids that
     * mismatch entirely.
     */
    public static GitClient forRepoContaining(Path startDir) throws IOException, InterruptedException {
        GitClient probe = new GitClient(startDir);
        String topLevel = probe.run(List.of("rev-parse", "--show-toplevel")).strip();
        return new GitClient(Path.of(topLevel));
    }

    public String run(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoDir.toFile());
        Process process = pb.start();

        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("git command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IOException("git command failed (" + process.exitValue() + "): "
                    + String.join(" ", command) + "\n" + stderr);
        }
        return stdout;
    }
}
