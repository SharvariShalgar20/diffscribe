package com.pragent.backend.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds github.token from application.yml (which itself reads it from the
 * GITHUB_TOKEN env var). A dedicated properties record instead of scattered
 * @Value("${github.token}") annotations - one typed place, easy to test.
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String token) {
}
