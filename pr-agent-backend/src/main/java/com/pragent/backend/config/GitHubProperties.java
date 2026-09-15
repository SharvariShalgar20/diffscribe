package com.pragent.backend.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the github.token property from application.yml (which itself reads
 * it from the GITHUB_TOKEN environment variable via Spring's placeholder
 * syntax). A dedicated properties record instead of scattering field-level
 * value-injection annotations across classes - one typed place, easy to test.
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String token) {
}
