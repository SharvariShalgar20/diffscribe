package com.pragent.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based reviewer notes computed from the diff text alone. No model is
 * involved, so the results are deterministic. Notes are flags to look at,
 * not verdicts, and a note never prints a matched secret value.
 */
final class RiskFlags {

    record FileDiff(String path, int added, int removed, List<String> addedLines) {}

    static final int LARGE_CHANGE_LINES = 500;

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/.+? b/(.+)$");

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".scala", ".groovy", ".py", ".js", ".jsx", ".ts", ".tsx",
            ".go", ".rs", ".c", ".cc", ".cpp", ".h", ".hpp", ".cs", ".rb", ".php", ".swift");

    private static final Pattern SENSITIVE_PATH = Pattern.compile(
            "(auth(?!or)|security|login|password|credential|secret|token|permission|oauth|jwt|crypto"
                    + "|\\.pem$|\\.key$|\\.p12$|\\.jks$)");

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("ghp_[A-Za-z0-9]{20,}"),
            Pattern.compile("github_pat_[A-Za-z0-9_]{20,}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----"));

    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']");

    private RiskFlags() {}

    static List<FileDiff> parse(List<String> chunks) {
        List<FileDiff> files = new ArrayList<>();
        for (String chunk : chunks) {
            String path = null;
            int added = 0;
            int removed = 0;
            boolean inHunk = false;
            List<String> addedLines = new ArrayList<>();
            for (String line : chunk.split("\\R")) {
                Matcher m = DIFF_HEADER.matcher(line);
                if (m.matches()) {
                    if (path != null) {
                        files.add(new FileDiff(path, added, removed, addedLines));
                    }
                    path = m.group(1).strip();
                    added = 0;
                    removed = 0;
                    inHunk = false;
                    addedLines = new ArrayList<>();
                } else if (path != null) {
                    // File headers ("--- a/x", "+++ b/x") come before the first @@,
                    // so only count +/- lines once inside a hunk.
                    if (line.startsWith("@@")) {
                        inHunk = true;
                    } else if (inHunk && line.startsWith("+")) {
                        added++;
                        addedLines.add(line.substring(1));
                    } else if (inHunk && line.startsWith("-")) {
                        removed++;
                    }
                }
            }
            if (path != null) {
                files.add(new FileDiff(path, added, removed, addedLines));
            }
        }
        return files;
    }

    static List<String> notes(List<String> chunks) {
        List<FileDiff> files = parse(chunks);
        if (files.isEmpty()) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();

        List<String> secretFiles = files.stream()
                .filter(RiskFlags::addsPossibleSecret).map(FileDiff::path).toList();
        if (!secretFiles.isEmpty()) {
            notes.add("Possible secret or credential added in " + names(secretFiles)
                    + ". Check before merging.");
        }

        List<String> sensitive = files.stream().map(FileDiff::path)
                .filter(p -> !TypeRules.isDocs(p) && !TypeRules.isTest(p))
                .filter(p -> SENSITIVE_PATH.matcher(p.toLowerCase(Locale.ROOT)).find())
                .toList();
        if (!sensitive.isEmpty()) {
            notes.add("Touches files with security-related names (worth a closer look): "
                    + names(sensitive) + ".");
        }

        boolean hasSource = files.stream()
                .anyMatch(f -> isSource(f.path()) && !TypeRules.isTest(f.path()));
        boolean hasTests = files.stream().anyMatch(f -> TypeRules.isTest(f.path()));
        if (hasSource && !hasTests) {
            notes.add("No test files were changed alongside the source changes.");
        }

        List<String> buildFiles = files.stream().map(FileDiff::path)
                .filter(TypeRules::isBuildOrConfig).toList();
        boolean hasOtherFiles = files.stream().anyMatch(f -> !TypeRules.isBuildOrConfig(f.path()));
        if (!buildFiles.isEmpty() && hasOtherFiles) {
            notes.add("Build or dependency configuration changed: " + names(buildFiles) + ".");
        }

        int changedLines = files.stream().mapToInt(f -> f.added() + f.removed()).sum();
        if (changedLines > LARGE_CHANGE_LINES) {
            notes.add("Large change (" + changedLines + " lines across " + files.size()
                    + " files). Consider splitting it into smaller PRs.");
        }

        return notes;
    }

    static String render(List<String> notes) {
        if (notes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n---\n**Reviewer notes (automated)**\n\n");
        for (String note : notes) {
            sb.append("- ").append(note).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static boolean isSource(String path) {
        String name = TypeRules.fileName(path).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SOURCE_EXTENSIONS.contains(name.substring(dot));
    }

    private static boolean addsPossibleSecret(FileDiff file) {
        boolean checkAssignments = !TypeRules.isTest(file.path());
        for (String line : file.addedLines()) {
            for (Pattern p : SECRET_PATTERNS) {
                if (p.matcher(line).find()) {
                    return true;
                }
            }
            if (checkAssignments && SECRET_ASSIGNMENT.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static String names(List<String> paths) {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(5, paths.size()); i++) {
            shown.add("`" + paths.get(i) + "`");
        }
        String joined = String.join(", ", shown);
        return paths.size() > 5 ? joined + " and " + (paths.size() - 5) + " more" : joined;
    }
}