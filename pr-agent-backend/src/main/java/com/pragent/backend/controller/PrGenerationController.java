package com.pragent.backend.controller;

import com.pragent.backend.dto.GenerateRequest;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.service.PrDescriptionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PrGenerationController {

    private final PrDescriptionService prDescriptionService;

    public PrGenerationController(PrDescriptionService prDescriptionService) {
        this.prDescriptionService = prDescriptionService;
    }

    @PostMapping("/generate-pr-description")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
        if (request.diff() == null || request.diff().isBlank()) {
            return ResponseEntity.badRequest().body("diff must not be empty");
        }
        return ResponseEntity.ok(prDescriptionService.generate(request.diff()));
    }
}
