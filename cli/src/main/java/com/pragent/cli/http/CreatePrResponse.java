package com.pragent.cli.http;

public record CreatePrResponse(UpdatePrResponse.GeneratedPr generated, int prNumber, String prUrl,
                               boolean created) {}