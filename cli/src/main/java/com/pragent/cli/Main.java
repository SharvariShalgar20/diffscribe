package com.pragent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Phase 0 skeleton.
 *
 * This does nothing useful yet — it exists to prove the CLI project builds,
 * packages into a runnable jar, and parses arguments correctly. Phase 1 will
 * replace the body of run() with actual `git diff` extraction and filtering.
 */
@Command(
        name = "pr-agent",
        mixinStandardHelpOptions = true,
        version = "pr-agent-cli 0.1.0",
        description = "Generates PR titles/descriptions from a git diff (skeleton, no real logic yet)."
)
public class Main implements Runnable {

    @Option(names = {"--backend-url"}, description = "Base URL of the pr-agent backend service.",
            defaultValue = "http://localhost:8080")
    private String backendUrl;

    @Override
    public void run() {
        System.out.println("pr-agent CLI skeleton is running.");
        System.out.println("Configured backend URL: " + backendUrl);
        System.out.println("(Phase 1 will make this actually read `git diff` and print a cleaned diff.)");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
