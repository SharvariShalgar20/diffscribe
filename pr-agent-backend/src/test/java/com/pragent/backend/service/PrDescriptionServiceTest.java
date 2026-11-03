package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.ollama.OllamaHealthChecker;
import com.pragent.backend.ollama.OllamaUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PrDescriptionServiceTest {

    private static final String GOOD_ANSWER = "TYPE: feat\nTITLE: add version endpoint\n"
            + "WHAT: Adds a version endpoint.\nWHY: Not evident from the diff.\n"
            + "TEST: Call the endpoint and check the response.";

    private static final String JAVA_DIFF = "diff --git a/src/main/java/App.java b/src/main/java/App.java\n+x\n";
    private static final String README_DIFF = "diff --git a/README.md b/README.md\n+x\n";

    private final OllamaHealthChecker healthChecker = mock(OllamaHealthChecker.class);

    private ChatClient clientAnswering(String first, String... more) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(first, more);
        return chatClient;
    }

    @Test
    void failsFastWithoutTouchingTheModelWhenOllamaIsDown() {
        ChatClient chatClient = mock(ChatClient.class);
        doThrow(new OllamaUnavailableException("Ollama is not reachable", null))
                .when(healthChecker).checkReachableOrThrow();
        PrDescriptionService service = new PrDescriptionService(chatClient, healthChecker);

        assertThrows(OllamaUnavailableException.class, () -> service.generate(List.of("some diff")));
        verifyNoInteractions(chatClient);
    }

    @Test
    void buildsTitleAndDescriptionFromAGoodAnswer() {
        PrDescriptionService service = new PrDescriptionService(clientAnswering(GOOD_ANSWER), healthChecker);

        PrDescription result = service.generate(List.of(JAVA_DIFF));

        assertEquals("feat: add version endpoint", result.title());
        assertEquals(ChangeType.FEAT, result.type());
        assertTrue(result.description().startsWith("## What changed\nAdds a version endpoint."));
        assertTrue(result.description().contains("## Why\nNot evident from the diff."));
        assertTrue(result.description().contains("## How to test\n"));
    }

    @Test
    void retriesOnceWhenTheFirstAnswerCannotBeRead() {
        PrDescriptionService service = new PrDescriptionService(
                clientAnswering("sorry, here is some rambling", GOOD_ANSWER), healthChecker);

        PrDescription result = service.generate(List.of(JAVA_DIFF));

        assertEquals(ChangeType.FEAT, result.type());
    }

    @Test
    void givesUpWithAClearErrorAfterTwoBadAnswers() {
        PrDescriptionService service = new PrDescriptionService(
                clientAnswering("nonsense", "more nonsense"), healthChecker);

        assertThrows(ModelOutputException.class, () -> service.generate(List.of(JAVA_DIFF)));
    }

    @Test
    void pathRulesOverrideTheModelsTypeAndTheTitlePrefixFollows() {
        PrDescriptionService service = new PrDescriptionService(clientAnswering(GOOD_ANSWER), healthChecker);

        PrDescription result = service.generate(List.of(README_DIFF));

        assertEquals(ChangeType.DOCS, result.type());
        assertTrue(result.title().startsWith("docs: "), result.title());
    }

    @Test
    void appendsReviewerNotesWhenSourceChangesHaveNoTests() {
        PrDescriptionService service = new PrDescriptionService(clientAnswering(GOOD_ANSWER), healthChecker);

        PrDescription result = service.generate(List.of(JAVA_DIFF));

        assertTrue(result.description().contains("Reviewer notes (automated)"));
        assertTrue(result.description().contains("No test files"));
    }

    @Test
    void docsOnlyChangeGetsNoReviewerNotes() {
        PrDescriptionService service = new PrDescriptionService(clientAnswering(GOOD_ANSWER), healthChecker);

        PrDescription result = service.generate(List.of(README_DIFF));

        assertFalse(result.description().contains("Reviewer notes"));
    }
}