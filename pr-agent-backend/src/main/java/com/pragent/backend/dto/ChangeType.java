package com.pragent.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Constrains the LLM's "type" output to a fixed set of conventional-commit
 * categories, rather than a free String. Spring AI turns this into a schema
 * constraint the model must pick from - this is what actually prevents
 * arbitrary/incorrect values, not just prompt wording.
 */
public enum ChangeType {
    @JsonProperty("feat") FEAT,
    @JsonProperty("fix") FIX,
    @JsonProperty("refactor") REFACTOR,
    @JsonProperty("docs") DOCS,
    @JsonProperty("test") TEST,
    @JsonProperty("chore") CHORE
}
