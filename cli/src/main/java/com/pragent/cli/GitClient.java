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
