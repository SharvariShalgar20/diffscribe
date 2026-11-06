package com.pragent.backend.dto;

import java.util.List;

public record CreatePrRequest(String repoOwner, String repoName, String branch, String base,
                              List<String> diffChunks) {}