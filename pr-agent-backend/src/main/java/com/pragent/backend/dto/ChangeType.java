package com.pragent.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Constrains the LLM's "type" output to a fixed set of conventional-commit
 * categories, rather than a free String. Spring AI turns this into a schema
 * constraint the model must pick from - this is what actually prevents
 * arbitrary/incorrect values, not just prompt wording.
 */
public enum ChangeType {
    FEAT,
    FIX,
    REFACTOR,
    DOCS,
    TEST,
    CHORE;

    @JsonCreator
    public static ChangeType fromValue(String value) {
        return ChangeType.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
