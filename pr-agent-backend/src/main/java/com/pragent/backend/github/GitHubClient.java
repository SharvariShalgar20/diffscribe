package com.pragent.backend.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pragent.backend.config.GitHubProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Thin wrapper around the GitHub REST API - only the two operations this
 * project needs (find an open PR by branch, update its title/body). Not a
 * general-purpose GitHub client.
 */
@Component
public class GitHubClient {

    // GitHub's list-PRs response has far more fields than we need - only
    // deserialize what we actually use.
    private record PullRequestSummary(int number) {}

    private record UpdatePrPayload(String title, String body) {}

    private record UpdatedPr(@JsonProperty("html_url") String htmlUrl) {}

    private final RestClient restClient;

    public GitHubClient(GitHubProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /**
     * GitHub requires the "owner:branch" format for the head filter, even
     * when querying within that same owner's repo.
     */
    public int findOpenPrNumber(String owner, String repo, String branch) {
        List<PullRequestSummary> prs = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "open")
                        .queryParam("head", owner + ":" + branch)
                        .build(owner, repo))
                .retrieve()
                .body(new ParameterizedTypeReference<List<PullRequestSummary>>() {});

        if (prs == null || prs.isEmpty()) {
            throw new NoSuchElementException(
                    "No open PR found for branch '" + branch + "' in " + owner + "/" + repo);
        }
        return prs.get(0).number();
    }

    /**
     * @return the PR's real html_url as returned by GitHub, rather than
     * constructing it manually - avoids subtly wrong URLs on GitHub Enterprise
     * hosts or if GitHub's URL format ever changes.
     */
    public String updatePr(String owner, String repo, int prNumber, String title, String body) {
        UpdatedPr updated = restClient.patch()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber)
                .body(new UpdatePrPayload(title, body))
                .retrieve()
                .body(UpdatedPr.class);
        return updated != null ? updated.htmlUrl() : null;
    }
}