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
import java.util.Optional;

@Component
public class GitHubClient {

    public record PullRequestRef(int number, String url) {}

    private record PullRequestSummary(int number) {}

    private record UpdatePrPayload(String title, String body) {}

    private record UpdatedPr(@JsonProperty("html_url") String htmlUrl) {}

    private record CreatePrPayload(String title, String head, String base, String body) {}

    private record CreatedPr(int number, @JsonProperty("html_url") String htmlUrl) {}

    private record LabelName(String name) {}

    private record CreateLabelPayload(String name, String color, String description) {}

    private record SetLabelsPayload(List<String> labels) {}

    private final RestClient restClient;

    public GitHubClient(GitHubProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public Optional<Integer> findOpenPr(String owner, String repo, String branch) {
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
                return Optional.empty();
            }
            return Optional.of(prs.get(0).number());
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    public int findOpenPrNumber(String owner, String repo, String branch) {
        return findOpenPr(owner, repo, branch).orElseThrow(() -> new NoSuchElementException(
                "No open PR found for branch '" + branch + "' in " + owner + "/" + repo));
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
     * GitHub answers 422 when the head branch isn't on GitHub, when there are no
     * commits between base and head, or when a PR already exists. That is handled
     * here and not in translate(), because a 422 from other calls means different things.
     */
    public PullRequestRef createPr(String owner, String repo, String head, String base,
                                   String title, String body) {
        try {
            CreatedPr created = restClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                    .body(new CreatePrPayload(title, head, base, body))
                    .retrieve()
                    .body(CreatedPr.class);
            if (created == null) {
                throw new IllegalStateException("GitHub returned an empty response when creating the PR.");
            }
            return new PullRequestRef(created.number(), created.htmlUrl());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                throw new GitHubValidationException("GitHub could not create the PR (422). Check that branch '"
                        + head + "' is pushed to GitHub, has commits that '" + base
                        + "' does not, and has no PR yet. GitHub said: " + shorten(e.getResponseBodyAsString()));
            }
            throw translate(e);
        }
    }

    public List<String> getLabelNames(String owner, String repo, int issueNumber) {
        List<LabelName> labels = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{number}/labels", owner, repo, issueNumber)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LabelName>>() {});
        return labels == null ? List.of() : labels.stream().map(LabelName::name).toList();
    }

    /** Creates the label if it doesn't exist. GitHub answers 422 when it already does. */
    public void ensureLabelExists(String owner, String repo, String name, String color, String description) {
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/labels", owner, repo)
                    .body(new CreateLabelPayload(name, color, description))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != 422) {
                throw e;
            }
        }
    }

    /** Replaces the PR's whole label list, so callers pass the full desired set. */
    public void setLabels(String owner, String repo, int issueNumber, List<String> labels) {
        restClient.put()
                .uri("/repos/{owner}/{repo}/issues/{number}/labels", owner, repo, issueNumber)
                .body(new SetLabelsPayload(labels))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Maps GitHub's raw HTTP errors to distinct, actionable exceptions.
     * GitHub signals rate limiting with 403 or 429 plus rate-limit headers,
     * so the status code alone can't distinguish "rate limited" from "forbidden".
     */
    static RuntimeException translate(HttpClientErrorException e) {
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

    private static String describeWait(HttpHeaders headers) {
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

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}