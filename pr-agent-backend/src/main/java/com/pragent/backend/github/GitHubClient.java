package com.pragent.backend.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pragent.backend.config.GitHubProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Component
public class GitHubClient {

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

    public int findOpenPrNumber(String owner, String repo, String branch) {
        try {
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
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    public String updatePr(String owner, String repo, int prNumber, String title, String body) {
        try {
            UpdatedPr updated = restClient.patch()
                    .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber)
                    .body(new UpdatePrPayload(title, body))
                    .retrieve()
                    .body(UpdatedPr.class);
            return updated != null ? updated.htmlUrl() : null;
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    /**
     * Maps GitHub's raw HTTP errors to distinct, actionable exceptions.
     * GitHub signals rate limiting with 403 or 429 plus rate-limit headers,
     * so the status code alone can't distinguish "rate limited" from
     * "forbidden".
     */
    private RuntimeException translate(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        HttpHeaders headers = e.getResponseHeaders();

        boolean rateLimited = (status == 403 || status == 429) && headers != null
                && ("0".equals(headers.getFirst("x-ratelimit-remaining"))
                || headers.getFirst("retry-after") != null);
        if (rateLimited) {
            return new GitHubRateLimitException("GitHub API rate limit reached. " + describeWait(headers));
        }
        if (status == 401) {
            return new GitHubAuthException(
                    "GitHub rejected the token (401). It may be expired, revoked, or missing the 'repo' scope.");
        }
        if (status == 403) {
            return new GitHubAuthException(
                    "GitHub denied access (403). The token lacks permission for this repository.");
        }
        if (status == 404) {
            return new NoSuchElementException(
                    "GitHub returned 404. Check the repo owner/name, and note that private repos "
                            + "return 404 when the token lacks the 'repo' scope.");
        }
        return e;
    }

    private String describeWait(HttpHeaders headers) {
        String retryAfter = headers.getFirst("retry-after");
        if (retryAfter != null) {
            return "Retry in " + retryAfter + " seconds.";
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (reset != null) {
            try {
                Duration wait = Duration.between(Instant.now(), Instant.ofEpochSecond(Long.parseLong(reset)));
                return "Limit resets in about " + Math.max(1, wait.toMinutes()) + " minute(s).";
            } catch (NumberFormatException ignored) {
                // fall through to the generic message
            }
        }
        return "Try again later.";
    }
}