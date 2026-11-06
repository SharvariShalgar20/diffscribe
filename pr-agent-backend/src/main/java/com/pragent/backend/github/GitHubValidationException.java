package com.pragent.backend.github;

public class GitHubValidationException extends RuntimeException {
    public GitHubValidationException(String message) {
        super(message);
    }
}