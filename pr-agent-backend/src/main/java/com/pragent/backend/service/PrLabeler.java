package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.github.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Keeps one "type: ..." label on the PR in sync with the generated type.
 * Best effort: the PR text is already updated by the time this runs, so any
 * failure here is logged and never fails the request.
 */
@Service
public class PrLabeler {

    private static final Logger log = LoggerFactory.getLogger(PrLabeler.class);

    private final GitHubClient gitHubClient;
    private final boolean enabled;

    public PrLabeler(GitHubClient gitHubClient, @Value("${pragent.labels.enabled:true}") boolean enabled) {
        this.gitHubClient = gitHubClient;
        this.enabled = enabled;
    }

    public void applyTypeLabel(String owner, String repo, int prNumber, ChangeType type) {
        if (!enabled) {
            return;
        }
        try {
            List<String> current = gitHubClient.getLabelNames(owner, repo, prNumber);
            if (TypeLabels.isUpToDate(current, type)) {
                return;
            }
            TypeLabels.Label wanted = TypeLabels.of(type);
            gitHubClient.ensureLabelExists(owner, repo, wanted.name(), wanted.color(), wanted.description());
            gitHubClient.setLabels(owner, repo, prNumber, TypeLabels.withTypeLabel(current, type));
        } catch (RuntimeException e) {
            log.warn("Could not set the type label on {}/{}#{}: {}", owner, repo, prNumber, e.getMessage());
        }
    }
}