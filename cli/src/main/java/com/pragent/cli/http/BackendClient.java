package com.pragent.cli.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to the pr-agent backend over HTTP. Uses Jackson to build/parse JSON
 * rather than hand-building strings - manual JSON escaping caused repeated
 * bugs during manual testing.
 */
public class BackendClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        // A newer backend may add response fields; an older CLI should ignore them, not fail.
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public UpdatePrResponse updatePr(String repoOwner, String repoName, String branch, List<String> diffChunks)
            throws IOException, InterruptedException {
        return post("/api/pr/update", new UpdatePrRequest(repoOwner, repoName, branch, diffChunks),
                UpdatePrResponse.class);
    }

    public CreatePrResponse createPr(String repoOwner, String repoName, String branch, String base,
                                     List<String> diffChunks) throws IOException, InterruptedException {
        return post("/api/pr/create", new CreatePrRequest(repoOwner, repoName, branch, base, diffChunks),
                CreatePrResponse.class);
    }

    private <T> T post(String path, Object requestBody, Class<T> responseType)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                // Several chunks mean several sequential model calls on a small local model.
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Could not connect to backend at " + baseUrl
                    + " - is it running? (cd pr-agent-backend && ./mvnw spring-boot:run)", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("Backend did not respond within 5 minutes - it may be stuck processing "
                    + "a large/slow diff, or Ollama may be unresponsive.", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Backend returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), responseType);
    }
}