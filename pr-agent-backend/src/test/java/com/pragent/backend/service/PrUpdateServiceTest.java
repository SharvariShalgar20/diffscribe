package com.pragent.backend.service;

import com.pragent.backend.dto.*;
import com.pragent.backend.github.GitHubClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class PrUpdateServiceTest {

    private final PrDescriptionService descriptions = mock(PrDescriptionService.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final PrLabeler labeler = mock(PrLabeler.class);
    private final PrUpdateService service = new PrUpdateService(descriptions, github, labeler);

    private final UpdatePrRequest request = new UpdatePrRequest("owner", "repo", "branch", List.of("diff"));

    private final CreatePrRequest createRequest =
            new CreatePrRequest("owner", "repo", "branch", "main", List.of("diff"));
    private final PrDescription sampleDescription =
            new PrDescription("feat: add thing", "body", ChangeType.FEAT);

    @Test
    void createsThePrWhenNoneExists() {
        when(github.findOpenPr("owner", "repo", "branch")).thenReturn(Optional.empty());
        when(descriptions.generate(List.of("diff"))).thenReturn(sampleDescription);
        when(github.createPr("owner", "repo", "branch", "main", "feat: add thing", "body"))
                .thenReturn(new GitHubClient.PullRequestRef(9, "https://github.com/owner/repo/pull/9"));

        CreatePrResponse response = service.createOrUpdatePr(createRequest);

        assertTrue(response.created());
        assertEquals(9, response.prNumber());
        assertEquals("https://github.com/owner/repo/pull/9", response.prUrl());
        verify(labeler).applyTypeLabel("owner", "repo", 9, ChangeType.FEAT);
        verify(github, never()).updatePr(any(), any(), anyInt(), any(), any());
    }

    @Test
    void updatesInsteadWhenAPrAlreadyExists() {
        when(github.findOpenPr("owner", "repo", "branch")).thenReturn(Optional.of(8));
        when(descriptions.generate(List.of("diff"))).thenReturn(sampleDescription);
        when(github.updatePr("owner", "repo", 8, "feat: add thing", "body"))
                .thenReturn("https://github.com/owner/repo/pull/8");

        CreatePrResponse response = service.createOrUpdatePr(createRequest);

        assertFalse(response.created());
        assertEquals(8, response.prNumber());
        verify(github, never()).createPr(any(), any(), any(), any(), any(), any());
    }

    @Test
    void generatesUpdatesAndThenLabelsTheFoundPr() {
        PrDescription generated = new PrDescription("feat: add thing", "## What changed\nthing", ChangeType.FEAT);
        when(github.findOpenPrNumber("owner", "repo", "branch")).thenReturn(8);
        when(descriptions.generate(List.of("diff"))).thenReturn(generated);
        when(github.updatePr("owner", "repo", 8, "feat: add thing", "## What changed\nthing"))
                .thenReturn("https://github.com/owner/repo/pull/8");

        UpdatePrResponse response = service.updateExistingPr(request);

        assertEquals(8, response.prNumber());
        assertEquals("https://github.com/owner/repo/pull/8", response.prUrl());
        assertSame(generated, response.generated());
        verify(labeler).applyTypeLabel("owner", "repo", 8, ChangeType.FEAT);
    }

    @Test
    void missingPrFailsBeforeAnyModelWorkOrLabeling() {
        when(github.findOpenPrNumber("owner", "repo", "branch"))
                .thenThrow(new NoSuchElementException("no PR"));

        assertThrows(NoSuchElementException.class, () -> service.updateExistingPr(request));

        verifyNoInteractions(descriptions, labeler);
        verify(github, never()).updatePr(any(), any(), anyInt(), any(), any());
    }
}