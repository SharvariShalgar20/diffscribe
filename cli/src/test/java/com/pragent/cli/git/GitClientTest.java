package com.pragent.cli.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GitClientTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/SharvariShalgar20/diffscribe.git",
            "https://github.com/SharvariShalgar20/diffscribe",
            "git@github.com:SharvariShalgar20/diffscribe.git",
            "https://someuser:sometoken@github.com/SharvariShalgar20/diffscribe.git"
    })
    void parsesOwnerAndRepoFromCommonRemoteFormats(String url) {
        assertArrayEquals(new String[]{"SharvariShalgar20", "diffscribe"},
                GitClient.parseOwnerAndRepo(url));
    }

    @Test
    void rejectsNonGitHubUrls() {
        assertThrows(IllegalStateException.class,
                () -> GitClient.parseOwnerAndRepo("https://gitlab.com/owner/repo.git"));
    }

    @Test
    void rejectsUrlsMissingTheRepoPart() {
        assertThrows(IllegalStateException.class,
                () -> GitClient.parseOwnerAndRepo("https://github.com/onlyowner"));
    }
}