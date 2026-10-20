package com.pragent.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CliConfigTest {

    @Test
    void missingFileGivesEmptyValues(@TempDir Path dir) {
        CliConfig config = CliConfig.loadFrom(dir);

        assertTrue(config.backendUrl().isEmpty());
        assertTrue(config.defaultBase().isEmpty());
        assertTrue(config.maxChunkChars().isEmpty());
    }

    @Test
    void readsAllValues(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(".pragent.properties"),
                "backend.url=http://example:9000\ndefault.base=develop\nmax.chunk.chars=1234\n");

        CliConfig config = CliConfig.loadFrom(dir);

        assertEquals(Optional.of("http://example:9000"), config.backendUrl());
        assertEquals(Optional.of("develop"), config.defaultBase());
        assertEquals(Optional.of(1234), config.maxChunkChars());
    }

    @Test
    void invalidNumberIsIgnored(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(".pragent.properties"), "max.chunk.chars=lots\n");

        assertTrue(CliConfig.loadFrom(dir).maxChunkChars().isEmpty());
    }
}