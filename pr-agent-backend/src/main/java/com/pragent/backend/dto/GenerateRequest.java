package com.pragent.backend.dto;

import java.util.List;

public record GenerateRequest(List<String> diffChunks) {}