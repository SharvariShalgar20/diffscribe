package com.pragent.cli.http;

public record UpdatePrResponse(GeneratedPr generated, int prNumber, String prUrl) {
    public record GeneratedPr(String title, String description, String type) {}
}