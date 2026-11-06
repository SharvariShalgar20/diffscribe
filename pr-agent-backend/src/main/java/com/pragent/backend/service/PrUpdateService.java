package com.pragent.backend.service;

import com.pragent.backend.dto.CreatePrRequest;
import com.pragent.backend.dto.CreatePrResponse;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.dto.UpdatePrRequest;
import com.pragent.backend.dto.UpdatePrResponse;
import com.pragent.backend.github.GitHubClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

        prLabeler.applyTypeLabel(request.repoOwner(), request.repoName(), prNumber, generated.type());

        return new UpdatePrResponse(generated, prNumber, prUrl);
    }

    /** Updates the open PR for the branch, or creates one if there is none yet. */
    public CreatePrResponse createOrUpdatePr(CreatePrRequest request) {
        Optional<Integer> existing = gitHubClient.findOpenPr(
                request.repoOwner(), request.repoName(), request.branch());

        PrDescription generated = prDescriptionService.generate(request.diffChunks());

        int prNumber;
        String prUrl;
        boolean created;
        if (existing.isPresent()) {
            prNumber = existing.get();
            prUrl = gitHubClient.updatePr(request.repoOwner(), request.repoName(), prNumber,
                    generated.title(), generated.description());
            created = false;
        } else {
            GitHubClient.PullRequestRef ref = gitHubClient.createPr(
                    request.repoOwner(), request.repoName(), request.branch(), request.base(),
                    generated.title(), generated.description());
            prNumber = ref.number();
            prUrl = ref.url();
            created = true;
        }

        prLabeler.applyTypeLabel(request.repoOwner(), request.repoName(), prNumber, generated.type());

        return new CreatePrResponse(generated, prNumber, prUrl, created);
    }
}