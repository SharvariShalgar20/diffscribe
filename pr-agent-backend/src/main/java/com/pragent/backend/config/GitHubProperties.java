package com.pragent.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String token) {

    public GitHubProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "GITHUB_TOKEN is not set. Set it as an environment variable "
                            + "(never in application.yml) and restart the backend.");
        }
    }
}