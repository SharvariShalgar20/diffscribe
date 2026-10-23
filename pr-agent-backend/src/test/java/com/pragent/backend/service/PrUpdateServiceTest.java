package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.dto.UpdatePrRequest;
import com.pragent.backend.dto.UpdatePrResponse;
import com.pragent.backend.github.GitHubClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class PrUpdateServiceTest {

    private final PrDescriptionService descriptions = mock(PrDescriptionService.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final PrUpdateService service = new PrUpdateService(descriptions, github);

    private final UpdatePrRequest request = new UpdatePrRequest("owner", "repo", "branch", List.of("diff"));

    @Test
    void generatesThenUpdatesTheFoundPr() {
        PrDescription generated = new PrDescription("feat: add thing", "## What changed\nthing", ChangeType.FEAT);
        when(github.findOpenPrNumber("owner", "repo", "branch")).thenReturn(8);
        when(descriptions.generate(List.of("diff"))).thenReturn(generated);
        when(github.updatePr("owner", "repo", 8, "feat: add thing", "## What changed\nthing"))
                .thenReturn("https://github.com/owner/repo/pull/8");

        UpdatePrResponse response = service.updateExistingPr(request);

        assertEquals(8, response.prNumber());
        assertEquals("https://github.com/owner/repo/pull/8", response.prUrl());
        assertSame(generated, response.generated());
    }

    @Test
    void missingPrFailsBeforeAnyModelWork() {
        when(github.findOpenPrNumber("owner", "repo", "branch"))
                .thenThrow(new NoSuchElementException("no PR"));

        assertThrows(NoSuchElementException.class, () -> service.updateExistingPr(request));

        verifyNoInteractions(descriptions);
        verify(github, never()).updatePr(any(), any(), anyInt(), any(), any());
    }
}