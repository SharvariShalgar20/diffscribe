package com.pragent.cli.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffChunkerTest {

    private final DiffChunker chunker = new DiffChunker();

    // A fake per-file diff block: 71 characters when bodyLen is 50.
    private static String fileDiff(String name, int bodyLen) {
        return "diff --git a/" + name + " b/" + name + "\n+" + "x".repeat(bodyLen) + "\n";
    }

    @Test
    void smallDiffStaysInOneChunk() {
        String diff = fileDiff("A", 50) + fileDiff("B", 50);

        List<String> chunks = chunker.chunk(diff, 8000);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("a/A"));
        assertTrue(chunks.get(0).contains("a/B"));
    }

    @Test
    void splitsOnFileBoundariesWhenOverLimit() {
        String diff = fileDiff("A", 50) + fileDiff("B", 50);

        List<String> chunks = chunker.chunk(diff, 80);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("a/A"));
        assertFalse(chunks.get(0).contains("a/B"));
        assertTrue(chunks.get(1).contains("a/B"));
    }

    @Test
    void everyChunkStartsAtAFileBoundary() {
        String diff = fileDiff("A", 50) + fileDiff("B", 50) + fileDiff("C", 50);

        List<String> chunks = chunker.chunk(diff, 150);

        assertEquals(2, chunks.size());
        chunks.forEach(c -> assertTrue(c.startsWith("diff --git "), c));
    }

    @Test
    void oversizedSingleFileIsKeptWholeNotTruncated() {
        String diff = fileDiff("Big", 500);

        List<String> chunks = chunker.chunk(diff, 100);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("x".repeat(500)));
    }

    @Test
    void emptyInputDoesNotThrow() {
        List<String> chunks = chunker.chunk("", 100);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).isBlank());
    }
}