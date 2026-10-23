package com.pragent.backend.service;

import com.pragent.backend.ollama.OllamaHealthChecker;
import com.pragent.backend.ollama.OllamaUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PrDescriptionServiceTest {

    @Test
    void failsFastWithoutTouchingTheModelWhenOllamaIsDown() {
        ChatClient chatClient = mock(ChatClient.class);
        OllamaHealthChecker healthChecker = mock(OllamaHealthChecker.class);
        doThrow(new OllamaUnavailableException("Ollama is not reachable", null))
                .when(healthChecker).checkReachableOrThrow();

        PrDescriptionService service = new PrDescriptionService(chatClient, healthChecker);

        assertThrows(OllamaUnavailableException.class, () -> service.generate(List.of("some diff")));
        verifyNoInteractions(chatClient);
    }
}