package com.pragent.backend.github;

public class GitHubRateLimitException extends RuntimeException {
    public GitHubRateLimitException(String message) {
        super(message);
    }
}