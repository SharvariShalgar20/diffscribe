package com.pragent.cli.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads optional per-repo settings from .pragent.properties at the repo
 * root, if present. This class only knows how to read the file - precedence
 * (CLI flag > config value > hardcoded default) is enforced by the caller.
 *
 * Plain java.util.Properties rather than YAML/JSON: this needs exactly a
 * few flat key=value settings, and Properties handles that with zero added
 * dependencies. Revisit if this config ever needs nested structure.
 */
public class CliConfig {

    private final Properties properties;

    private CliConfig(Properties properties) {
        this.properties = properties;
    }

    public static CliConfig loadFrom(Path repoRoot) {
        Properties props = new Properties();
        Path configPath = repoRoot.resolve(".pragent.properties");
        if (Files.isRegularFile(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Warning: failed to read .pragent.properties: " + e.getMessage());
            }
        }
        return new CliConfig(props);
    }

    public Optional<String> backendUrl() {
        return Optional.ofNullable(properties.getProperty("backend.url"));
    }

    public Optional<String> defaultBase() {
        return Optional.ofNullable(properties.getProperty("default.base"));
    }

    public Optional<Integer> maxChunkChars() {
        String value = properties.getProperty("max.chunk.chars");
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            System.err.println("Warning: invalid max.chunk.chars value '" + value + "', ignoring.");
            return Optional.empty();
        }
    }
}