package com.pragent.backend.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import java.time.Duration;

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
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new LlmUnavailableException("Could not reach the model API (timeout or network problem). "
                    + "Check your internet connection and LLM_BASE_URL.", e);
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

    /** Maps raw HTTP errors to distinct exceptions. Messages never contain the API key. */
    static RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new LlmAuthException("The model API rejected the API key (" + status
                    + "). Check LLM_API_KEY, and that the key may use this model.");
        }
        if (status == 429) {
            Duration retryAfter = retryAfter(e.getResponseHeaders());
            String wait = retryAfter == null ? "" : " Try again in " + retryAfter.toSeconds() + " seconds.";
            return new LlmRateLimitException("The model API rate limit was reached "
                    + "(free tiers are limited)." + wait, retryAfter);
        }
        if (status >= 500) {
            return new LlmUnavailableException("The model API is having problems (HTTP " + status
                    + "). Try again shortly.", e);
        }
        return new IllegalStateException("The model API returned HTTP " + status + ": "
                + shorten(e.getResponseBodyAsString()), e);
    }

    private static Duration retryAfter(org.springframework.http.HttpHeaders headers) {
        if (headers == null || headers.getFirst(HttpHeaders.RETRY_AFTER) == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER).trim()));
        } catch (NumberFormatException ignored) {
            return null; // an HTTP-date value: not worth parsing, backoff is used instead
        }
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}