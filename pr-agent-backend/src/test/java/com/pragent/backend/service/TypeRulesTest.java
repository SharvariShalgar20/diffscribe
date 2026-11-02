package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeRulesTest {

    @Test
    void readsPathsFromDiffHeaders() {
        String chunk = "diff --git a/README.md b/README.md\nindex 1..2 100644\n+x\n"
                + "diff --git a/src/A.java b/src/A.java\n+y\n";

        assertEquals(List.of("README.md", "src/A.java"), TypeRules.changedPaths(List.of(chunk)));
    }

    @Test
    void docsOnlyIsDocs() {
        assertEquals(Optional.of(ChangeType.DOCS), TypeRules.fromPaths(List.of("README.md", "docs/guide.png")));
    }

    @Test
    void testsOnlyIsTest() {
        assertEquals(Optional.of(ChangeType.TEST),
                TypeRules.fromPaths(List.of("src/test/java/com/x/FooTest.java")));
    }

    @Test
    void buildFilesOnlyIsChore() {
        assertEquals(Optional.of(ChangeType.CHORE),
                TypeRules.fromPaths(List.of("pom.xml", ".github/workflows/ci.yml")));
    }

    @Test
    void sourceCodeLeavesTheDecisionToTheModel() {
        assertEquals(Optional.empty(), TypeRules.fromPaths(List.of("src/main/java/com/x/App.java")));
    }

    @Test
    void mixedKindsLeaveTheDecisionToTheModel() {
        assertEquals(Optional.empty(), TypeRules.fromPaths(List.of("README.md", "pom.xml")));
    }

    @Test
    void lookAlikesAreNotMisclassified() {
        assertEquals(Optional.empty(), TypeRules.fromPaths(List.of("src/main/java/Contest.java")));
        assertEquals(Optional.empty(), TypeRules.fromPaths(List.of("requirements.txt")));
    }

    @Test
    void noPathsMeansNoRule() {
        assertEquals(Optional.empty(), TypeRules.fromPaths(List.of()));
    }
}