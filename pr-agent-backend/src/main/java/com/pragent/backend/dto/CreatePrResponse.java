package com.pragent.backend.dto;

public record CreatePrResponse(PrDescription generated, int prNumber, String prUrl, boolean created) {}