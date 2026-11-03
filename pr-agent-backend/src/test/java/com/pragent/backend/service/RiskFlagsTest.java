package com.pragent.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskFlagsTest {

    private static String diff(String path, String... addedLines) {
        StringBuilder sb = new StringBuilder("diff --git a/" + path + " b/" + path + "\n"
                + "index 1..2 100644\n--- a/" + path + "\n+++ b/" + path + "\n@@ -1,1 +1,2 @@\n context\n");
        for (String line : addedLines) {
            sb.append('+').append(line).append('\n');
        }
        return sb.toString();
    }

    private static List<String> notes(String... chunks) {
        return RiskFlags.notes(List.of(chunks));
    }

    private static boolean has(List<String> notes, String text) {
        return notes.stream().anyMatch(n -> n.contains(text));
    }

    @Test
    void countsOnlyHunkLinesNotFileHeaders() {
        String chunk = diff("src/A.java", "one", "two") + "-removed line\n";

        RiskFlags.FileDiff file = RiskFlags.parse(List.of(chunk)).get(0);

        assertEquals("src/A.java", file.path());
        assertEquals(2, file.added());
        assertEquals(1, file.removed());
    }

    @Test
    void sourceWithoutTestsIsFlagged() {
        assertTrue(has(notes(diff("src/main/java/App.java", "int x;")), "No test files"));
    }

    @Test
    void sourceWithTestsIsNotFlagged() {
        List<String> notes = notes(diff("src/main/java/App.java", "int x;"),
                diff("src/test/java/AppTest.java", "int y;"));

        assertFalse(has(notes, "No test files"));
    }

    @Test
    void docsOnlyAndBuildOnlyChangesGetNoNotes() {
        assertTrue(notes(diff("README.md", "text")).isEmpty());
        assertTrue(notes(diff("pom.xml", "<a/>")).isEmpty());
    }

    @Test
    void securityRelatedNamesAreFlaggedButAuthorIsNot() {
        assertTrue(has(notes(diff("src/main/java/AuthService.java", "x")), "security-related"));
        assertFalse(has(notes(diff("src/main/java/AuthorList.java", "x")), "security-related"));
    }

    @Test
    void tokenOnAnAddedLineIsFlaggedWithoutPrintingIt() {
        String token = "ghp_" + "a".repeat(36);

        List<String> notes = notes(diff("config/app.txt", "key=" + token));

        assertTrue(has(notes, "Possible secret"));
        assertTrue(notes.stream().noneMatch(n -> n.contains(token)));
        assertTrue(has(notes, "config/app.txt"));
    }

    @Test
    void privateKeyHeaderIsFlagged() {
        assertTrue(has(notes(diff("deploy/key.txt", "-----BEGIN " + "PRIVATE KEY-----")), "Possible secret"));
    }

    @Test
    void removingASecretIsNotFlagged() {
        String chunk = diff("config/app.txt", "safe") + "-key=ghp_" + "a".repeat(36) + "\n";

        assertFalse(has(notes(chunk), "Possible secret"));
    }

    @Test
    void placeholdersAreNotSecrets() {
        assertFalse(has(notes(diff("src/main/resources/application.yaml", "  token: ${GITHUB_TOKEN:}")),
                "Possible secret"));
    }

    @Test
    void hardCodedPasswordAssignmentIsFlaggedInSourceButNotInTests() {
        String line = "String password = \"hunter2hunter2\";";

        assertTrue(has(notes(diff("src/main/java/Db.java", line)), "Possible secret"));
        assertFalse(has(notes(diff("src/test/java/DbTest.java", line)), "Possible secret"));
    }

    @Test
    void buildFilesAlongsideSourceAreMentioned() {
        List<String> notes = notes(diff("pom.xml", "<a/>"), diff("src/main/java/App.java", "int x;"));

        assertTrue(has(notes, "Build or dependency configuration changed"));
    }

    @Test
    void veryLargeChangesAreFlagged() {
        String[] lines = new String[RiskFlags.LARGE_CHANGE_LINES + 10];
        java.util.Arrays.fill(lines, "int x;");

        assertTrue(has(notes(diff("src/main/java/Big.java", lines)), "Large change"));
    }

    @Test
    void renderIsEmptyWithoutNotesAndAvoidsAHeading() {
        assertEquals("", RiskFlags.render(List.of()));

        String block = RiskFlags.render(List.of("a note"));

        assertTrue(block.contains("**Reviewer notes (automated)**"));
        assertTrue(block.contains("- a note"));
        assertFalse(block.contains("## "));
    }
}