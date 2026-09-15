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

    public PrUpdateService(PrDescriptionService prDescriptionService, GitHubClient gitHubClient) {
        this.prDescriptionService = prDescriptionService;
        this.gitHubClient = gitHubClient;
    }

    public UpdatePrResponse updateExistingPr(UpdatePrRequest request) {
        PrDescription generated = prDescriptionService.generate(request.diffChunks());

        int prNumber = gitHubClient.findOpenPrNumber(
                request.repoOwner(), request.repoName(), request.branch());

        String prUrl = gitHubClient.updatePr(
                request.repoOwner(), request.repoName(), prNumber,
                generated.title(), generated.description());

        return new UpdatePrResponse(generated, prNumber, prUrl);
    }
}