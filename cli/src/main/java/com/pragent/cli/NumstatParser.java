package com.pragent.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses `git diff --numstat` output.
 *
 * Format is: "<added>\t<deleted>\t<path>", or "-\t-\t<path>" for binary
 * files (git can't compute line-level added/deleted counts for binaries).
 */
public class NumstatParser {
    public static List<DiffFilter.FileChange> parse(String numstatOutput) {
        List<DiffFilter.FileChange> changes = new ArrayList<>();
        for (String line : numstatOutput.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) {
                continue;
            }
            boolean binary = parts[0].equals("-") && parts[1].equals("-");
            changes.add(new DiffFilter.FileChange(parts[2], binary));
        }
        return changes;
    }
}
