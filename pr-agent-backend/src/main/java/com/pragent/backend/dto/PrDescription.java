package com.pragent.backend.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record PrDescription(
        @JsonPropertyDescription("A short, conventional-commit-style PR title, e.g. 'feat: add diff chunking to CLI'")
        String title,

        @JsonPropertyDescription("Markdown-formatted description with exactly three sections in order: " + "'## What changed', '## Why', '## How to test'")
        String description,

        @JsonPropertyDescription("The type of change, chosen strictly by definition, not by file count or size")
        ChangeType type)
{

}
