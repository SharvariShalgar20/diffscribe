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
 * caused repeated bugs during manual Postman testing. A real JSON library
 * sidesteps that entire class of bug.
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
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Could not connect to backend at " + baseUrl
                    + " - is it running? (cd backend && mvn spring-boot:run)", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("Backend did not respond within 5 minutes - it may be stuck processing "
                    + "a large/slow diff, or Ollama may be unresponsive.", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Backend returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), UpdatePrResponse.class);
    }
}