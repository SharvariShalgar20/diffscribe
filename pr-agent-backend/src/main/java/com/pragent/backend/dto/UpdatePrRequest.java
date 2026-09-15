package com.pragent.backend.dto;

import java.util.List;

public record UpdatePrRequest(String repoOwner, String repoName, String branch, List<String> diffChunks) {}