package com.pragent.backend;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PrGenerationController {

    private static final String SYSTEM_PROMPT = """
            You are an assistant that writes pull request titles and descriptions
            from a git diff. Base everything strictly on the diff content - do not
            invent changes that are not shown in the diff. Write the title in
            conventional-commit style (type: short summary). Keep the description
            factual and concise.
            """;

    private final ChatClient chatClient;

    public PrGenerationController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/generate-pr-description")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
        if (request.diff() == null || request.diff().isBlank()) {
            return ResponseEntity.badRequest().body("diff must not be empty");
        }

        PrDescription result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Here is the diff:\n\n" + request.diff())
                .call()
                .entity(PrDescription.class);

        return ResponseEntity.ok(result);
    }
}
