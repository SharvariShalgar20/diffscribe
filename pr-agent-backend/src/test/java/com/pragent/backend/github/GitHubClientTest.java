package com.pragent.backend.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class GitHubClientTest {

    private static HttpClientErrorException error(HttpStatus status, HttpHeaders headers) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), headers, new byte[0], null);
    }

    @Test
    void forbiddenWithRemainingZeroIsARateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");

        RuntimeException result = GitHubClient.translate(error(HttpStatus.FORBIDDEN, headers));

        assertInstanceOf(GitHubRateLimitException.class, result);
    }

    @Test
    void tooManyRequestsWithRetryAfterReportsTheWait() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("retry-after", "30");

        RuntimeException result = GitHubClient.translate(error(HttpStatus.TOO_MANY_REQUESTS, headers));

        assertInstanceOf(GitHubRateLimitException.class, result);
        assertTrue(result.getMessage().contains("30 seconds"), result.getMessage());
    }

    @Test
    void resetHeaderIsConvertedToMinutes() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");
        headers.add("x-ratelimit-reset", String.valueOf(Instant.now().plusSeconds(600).getEpochSecond()));

        RuntimeException result = GitHubClient.translate(error(HttpStatus.FORBIDDEN, headers));

        assertInstanceOf(GitHubRateLimitException.class, result);
        assertTrue(result.getMessage().contains("minute"), result.getMessage());
    }

    @Test
    void forbiddenWithRequestsRemainingIsNotARateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "42");

        RuntimeException result = GitHubClient.translate(error(HttpStatus.FORBIDDEN, headers));

        assertInstanceOf(GitHubAuthException.class, result);
    }

    @Test
    void forbiddenWithNoHeadersIsAnAuthProblem() {
        RuntimeException result = GitHubClient.translate(error(HttpStatus.FORBIDDEN, new HttpHeaders()));

        assertInstanceOf(GitHubAuthException.class, result);
    }

    @Test
    void unauthorizedIsAnAuthProblemMentioningTheToken() {
        RuntimeException result = GitHubClient.translate(error(HttpStatus.UNAUTHORIZED, new HttpHeaders()));

        assertInstanceOf(GitHubAuthException.class, result);
        assertTrue(result.getMessage().contains("token"), result.getMessage());
    }

    @Test
    void notFoundBecomesNoSuchElement() {
        RuntimeException result = GitHubClient.translate(error(HttpStatus.NOT_FOUND, new HttpHeaders()));

        assertInstanceOf(NoSuchElementException.class, result);
    }

    @Test
    void unrelatedClientErrorsPassThroughUntouched() {
        HttpClientErrorException original = error(HttpStatus.UNPROCESSABLE_ENTITY, new HttpHeaders());

        assertSame(original, GitHubClient.translate(original));
    }
}