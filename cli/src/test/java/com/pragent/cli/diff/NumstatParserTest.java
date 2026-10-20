package com.pragent.cli.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NumstatParserTest {

    @Test
    void parsesTextAndBinaryEntries() {
        String output = "1\t1\tsrc/A.java\n1\t0\tsrc/B.java\n-\t-\ttarget/X.class\n";

        List<DiffFilter.FileChange> result = NumstatParser.parse(output);

        assertEquals(3, result.size());
        assertEquals(new DiffFilter.FileChange("src/A.java", false), result.get(0));
        assertEquals(new DiffFilter.FileChange("src/B.java", false), result.get(1));
        assertEquals(new DiffFilter.FileChange("target/X.class", true), result.get(2));
    }

    @Test
    void keepsSpacesInPaths() {
        List<DiffFilter.FileChange> result = NumstatParser.parse("2\t3\tdocs/my file.md\n");

        assertEquals("docs/my file.md", result.get(0).path());
    }

    @Test
    void ignoresBlankAndMalformedLines() {
        List<DiffFilter.FileChange> result = NumstatParser.parse("\ngarbage\n1\t1\tsrc/A.java\n   \n");

        assertEquals(1, result.size());
        assertEquals("src/A.java", result.get(0).path());
    }

    @Test
    void emptyOutputGivesEmptyList() {
        assertTrue(NumstatParser.parse("").isEmpty());
    }
}