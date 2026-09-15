package com.pragent.backend.controller;

import com.pragent.backend.dto.UpdatePrRequest;
import com.pragent.backend.dto.UpdatePrResponse;
import com.pragent.backend.service.PrUpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/pr")
public class PrUpdateController {

    private final PrUpdateService prUpdateService;

    public PrUpdateController(PrUpdateService prUpdateService) {
        this.prUpdateService = prUpdateService;
    }

    @PostMapping("/update")
    public ResponseEntity<?> update(@RequestBody UpdatePrRequest request) {
        if (isBlank(request.repoOwner()) || isBlank(request.repoName()) || isBlank(request.branch())) {
            return ResponseEntity.badRequest().body("repoOwner, repoName, and branch are required");
        }
        if (request.diffChunks() == null || request.diffChunks().isEmpty()) {
            return ResponseEntity.badRequest().body("diffChunks must not be empty");
        }

        try {
            UpdatePrResponse response = prUpdateService.updateExistingPr(request);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}