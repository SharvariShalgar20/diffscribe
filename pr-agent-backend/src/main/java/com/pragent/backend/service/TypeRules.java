package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides the change type from file paths alone when that is unambiguous
 * (every changed file is docs, or tests, or build/config). Judgement calls
 * like feat vs fix vs refactor stay with the model.
 */
final class TypeRules {

    private static final Pattern DIFF_HEADER = Pattern.compile("(?m)^diff --git a/.+? b/(.+)$");

    private static final Set<String> DOC_EXTENSIONS = Set.of(".md", ".mdx", ".rst", ".adoc");

    private static final Set<String> BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "gradle.properties", "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "dockerfile", "docker-compose.yml", "docker-compose.yaml", ".gitignore", ".gitattributes",
            "mvnw", "mvnw.cmd", "maven-wrapper.properties", ".pragent.properties");

    // Case-sensitive on purpose so "Contest.java" is not treated as a test file.
    private static final Pattern TEST_FILE = Pattern.compile(
            "^(test_.*|.*_test\\..*|.*\\.(test|spec)\\.[a-z]+|.*Tests?\\.(java|kt|scala|groovy))$");

    private TypeRules() {}

    static List<String> changedPaths(List<String> diffChunks) {
        List<String> paths = new ArrayList<>();
        for (String chunk : diffChunks) {
            Matcher m = DIFF_HEADER.matcher(chunk);
            while (m.find()) {
                paths.add(m.group(1).strip());
            }
        }
        return paths;
    }

    static Optional<ChangeType> fromPaths(List<String> paths) {
        if (paths.isEmpty()) {
            return Optional.empty();
        }
        if (paths.stream().allMatch(TypeRules::isDocs)) {
            return Optional.of(ChangeType.DOCS);
        }
        if (paths.stream().allMatch(TypeRules::isTest)) {
            return Optional.of(ChangeType.TEST);
        }
        if (paths.stream().allMatch(TypeRules::isBuildOrConfig)) {
            return Optional.of(ChangeType.CHORE);
        }
        return Optional.empty();
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean isDocs(String path) {
        String p = "/" + path.toLowerCase(Locale.ROOT);
        String name = fileName(p);
        return p.startsWith("/docs/")
                || name.startsWith("license")
                || DOC_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isTest(String path) {
        String p = "/" + path.toLowerCase(Locale.ROOT);
        return p.contains("/test/") || p.contains("/tests/") || p.contains("/__tests__/")
                || TEST_FILE.matcher(fileName(path)).matches();
    }

    private static boolean isBuildOrConfig(String path) {
        String p = "/" + path.toLowerCase(Locale.ROOT);
        return BUILD_FILES.contains(fileName(p))
                || p.startsWith("/.github/")
                || p.contains("/.mvn/");
    }
}