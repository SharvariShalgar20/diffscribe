package com.pragent.cli.diff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffFilterTest {

    private final DiffFilter filter = new DiffFilter();

    @Test
    void binaryFilesAreAlwaysNoise() {
        assertTrue(filter.isNoise(new DiffFilter.FileChange("docs/logo.png", true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "target/classes/App.class",
            "cli/target/notes.txt",
            "Foo.class",
            "lib/app.jar",
            "dependency-reduced-pom.xml",
            "cli/dependency-reduced-pom.xml",
            ".mvn/wrapper/maven-wrapper.jar"
    })
    void buildArtifactsAreNoise(String path) {
        assertTrue(filter.isNoise(new DiffFilter.FileChange(path, false)), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/Main.java",
            "pom.xml",
            "README.md",
            "src/target.txt",
            "targets/readme.md"
    })
    void realSourceFilesAreKept(String path) {
        assertFalse(filter.isNoise(new DiffFilter.FileChange(path, false)), path);
    }

    @Test
    void keepDropsNoiseAndPreservesOrder() {
        var a = new DiffFilter.FileChange("src/A.java", false);
        var noise = new DiffFilter.FileChange("target/A.class", true);
        var b = new DiffFilter.FileChange("src/B.java", false);

        assertEquals(List.of(a, b), filter.keep(List.of(a, noise, b)));
    }
}