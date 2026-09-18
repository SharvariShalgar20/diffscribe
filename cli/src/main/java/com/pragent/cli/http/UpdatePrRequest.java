package com.pragent.cli.http;

import java.util.List;

public record UpdatePrRequest(String repoOwner, String repoName, String branch, List<String> diffChunks) {}