package com.pragent.backend.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Talks to any server that implements POST {baseUrl}/chat/completions
 * (Gemini, Groq, OpenRouter, ...). The API key is only ever put in the
 * Authorization header and is never logged or included in error messages.
 */
public class OpenAiCompatibleProvider implements LlmProvider {

    private static final double TEMPERATURE = 0.2;

    private record ChatRequest(String model, List<Message> messages, double temperature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {}

    private final RestClient restClient;
    private final String model;

    public OpenAiCompatibleProvider(LlmProperties properties, RestClient.Builder builder) {
        this.model = properties.model();
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(new ChatRequest(model, messages, TEMPERATURE))
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            // Task 3 replaces this with distinct 401 / 429 / timeout exceptions.
            throw new IllegalStateException("The model API returned HTTP "
                    + e.getStatusCode().value() + ": " + shorten(e.getResponseBodyAsString()), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null) {
            throw new IllegalStateException("The model API returned no answer.");
        }
        return response.choices().get(0).message().content();
    }

    /** No pre-flight call: it would spend free-tier quota on every push. The key is validated at startup. */
    @Override
    public void checkAvailableOrThrow() {
        // intentionally empty
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}