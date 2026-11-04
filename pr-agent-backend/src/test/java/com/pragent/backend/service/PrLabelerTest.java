package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.github.GitHubClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrLabelerTest {

    private final GitHubClient github = mock(GitHubClient.class);

    @Test
    void createsAndAddsTheLabelOnAnUnlabeledPr() {
        when(github.getLabelNames("o", "r", 8)).thenReturn(List.of());

        new PrLabeler(github, true).applyTypeLabel("o", "r", 8, ChangeType.FEAT);

        verify(github).ensureLabelExists(eq("o"), eq("r"), eq("type: feat"), anyString(), anyString());
        verify(github).setLabels("o", "r", 8, List.of("type: feat"));
    }

    @Test
    void swapsTheOldTypeLabelAndKeepsTheUsersOwnLabels() {
        when(github.getLabelNames("o", "r", 8)).thenReturn(List.of("priority", "type: fix"));

        new PrLabeler(github, true).applyTypeLabel("o", "r", 8, ChangeType.FEAT);

        verify(github).setLabels("o", "r", 8, List.of("priority", "type: feat"));
    }

    @Test
    void doesNothingMoreWhenTheLabelIsAlreadyRight() {
        when(github.getLabelNames("o", "r", 8)).thenReturn(List.of("type: feat"));

        new PrLabeler(github, true).applyTypeLabel("o", "r", 8, ChangeType.FEAT);

        verify(github, never()).ensureLabelExists(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(github, never()).setLabels(anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void disabledMeansNoGitHubCalls() {
        new PrLabeler(github, false).applyTypeLabel("o", "r", 8, ChangeType.FEAT);

        verifyNoInteractions(github);
    }

    @Test
    void githubFailuresAreSwallowed() {
        when(github.getLabelNames("o", "r", 8)).thenThrow(new RuntimeException("403"));

        assertDoesNotThrow(() -> new PrLabeler(github, true).applyTypeLabel("o", "r", 8, ChangeType.FEAT));
    }
}