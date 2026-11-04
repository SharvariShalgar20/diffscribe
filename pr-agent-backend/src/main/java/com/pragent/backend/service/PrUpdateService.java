package com.pragent.backend.service;

import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.dto.UpdatePrRequest;
import com.pragent.backend.dto.UpdatePrResponse;
import com.pragent.backend.github.GitHubClient;
import org.springframework.stereotype.Service;

@Service
public class PrUpdateService {

    private final PrDescriptionService prDescriptionService;
    private final GitHubClient gitHubClient;
    private final PrLabeler prLabeler;

    public PrUpdateService(PrDescriptionService prDescriptionService, GitHubClient gitHubClient,
                           PrLabeler prLabeler) {
        this.prDescriptionService = prDescriptionService;
        this.gitHubClient = gitHubClient;
        this.prLabeler = prLabeler;
    }

    public UpdatePrResponse updateExistingPr(UpdatePrRequest request) {
        // Look the PR up first: if there isn't one, fail fast instead of
        // spending minutes of model time on a description nobody can use.
        int prNumber = gitHubClient.findOpenPrNumber(
                request.repoOwner(), request.repoName(), request.branch());

        PrDescription generated = prDescriptionService.generate(request.diffChunks());

        String prUrl = gitHubClient.updatePr(
                request.repoOwner(), request.repoName(), prNumber,
                generated.title(), generated.description());

        // Best effort: the PR text is already updated, so a labeling problem must not fail the request.
        prLabeler.applyTypeLabel(request.repoOwner(), request.repoName(), prNumber, generated.type());

        return new UpdatePrResponse(generated, prNumber, prUrl);
    }
}