package com.pragent.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a diff into size-bounded chunks, never splitting in the middle of
 * a single file's diff block.
 *
 * Each file's diff block starts with a line beginning "diff --git ", which
 * gives us a safe split point. Chunks are packed greedily up to the size
 * limit. If a single file's diff exceeds the limit on its own, it is kept
 * whole rather than truncated - a cut-off diff hunk would be worse than one
 * oversized chunk, and the backend (Phase 2+) needs to handle chunk size
 * defensively regardless.
 */
public class DiffChunker {
    public List<String> chunk(String fullDiff, int maxCharsPerChunk) {
        List<String> blocks = splitIntoFileBlocks(fullDiff);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (current.length() > 0 && current.length() + block.length() > maxCharsPerChunk) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(block);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of("") : chunks;
    }

    private List<String> splitIntoFileBlocks(String fullDiff) {
        String[] lines = fullDiff.split("\n", -1);
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("diff --git ") && current.length() > 0) {
                blocks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }
}
