package com.pragent.cli.http;

import java.util.List;

public record CreatePrRequest(String repoOwner, String repoName, String branch, String base,
                              List<String> diffChunks) {}