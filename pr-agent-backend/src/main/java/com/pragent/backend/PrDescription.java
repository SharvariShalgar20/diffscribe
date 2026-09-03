package com.pragent.backend;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record PrDescription(@JsonPropertyDescription("A short, conventional-commit-style PR title, e.g. 'feat: add diff chunking to CLI'")
                            String title,

                            @JsonPropertyDescription("A description of what changed and why, based strictly on the diff provided")
                            String description,

                            @JsonPropertyDescription("The type of change: one of feat, fix, refactor, docs, test, chore")
                            String type) {

}
