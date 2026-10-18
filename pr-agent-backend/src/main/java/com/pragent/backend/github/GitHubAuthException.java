package com.pragent.backend.github;

public class GitHubAuthException extends RuntimeException {
    public GitHubAuthException(String message) {
        super(message);
    }
}