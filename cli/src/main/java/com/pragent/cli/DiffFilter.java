package com.pragent.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which changed files are "noise" and should be excluded from the
 * diff sent to the LLM.
 *
 * Scope note: Java/Maven-only patterns for now. This will likely need to
 * become configurable (Phase 6) once this runs against non-Java repos.
 */
public class DiffFilter {
    private static final List<Pattern> NOISE_PATTERNS = List.of(
            Pattern.compile("(^|.*/)target/.*"),
            Pattern.compile(".*\\.class$"),
            Pattern.compile(".*\\.jar$"),
            Pattern.compile(".*dependency-reduced-pom\\.xml$"),
            Pattern.compile("(^|.*/)\\.mvn/wrapper/maven-wrapper\\.jar$")
    );

    public record FileChange(String path, boolean binary) {}

    public boolean isNoise(FileChange change) {
        if (change.binary()) {
            return true;
        }
        for (Pattern pattern : NOISE_PATTERNS) {
            if (pattern.matcher(change.path()).matches()) {
                return true;
            }
        }
        return false;
    }

    public List<FileChange> keep(List<FileChange> changes) {
        List<FileChange> kept = new ArrayList<>();
        for (FileChange change : changes) {
            if (!isNoise(change)) {
                kept.add(change);
            }
        }
        return kept;
    }
}
