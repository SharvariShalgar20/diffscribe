package com.pragent.cli.http;

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
 * rather than hand-building strings - manual JSON escaping is exactly what
 * caused repeated bugs during manual Postman testing (unescaped newlines,
 * literal ${...} sequences being misread as template interpolation). A real
 * JSON library sidesteps that entire class of bug rather than us reproducing
 * it in Java.
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
        this.objectMapper = new ObjectMapper();
    }

    public UpdatePrResponse updatePr(String repoOwner, String repoName, String branch, List<String> diffChunks)
            throws IOException, InterruptedException {
        UpdatePrRequest requestBody = new UpdatePrRequest(repoOwner, repoName, branch, diffChunks);
        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/pr/update"))
                .header("Content-Type", "application/json")
                // Map-reduce over several chunks means several sequential LLM
                // calls on a small local model - can genuinely take minutes.
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Backend returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), UpdatePrResponse.class);
    }
}