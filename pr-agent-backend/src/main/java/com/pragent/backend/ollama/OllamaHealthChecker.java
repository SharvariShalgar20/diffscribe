package com.pragent.backend.ollama;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fast pre-flight check for Ollama connectivity. Spring AI's own retry
 * logic can take several minutes to give up when Ollama is fully down,
 * which then surfaces to the CLI as a generic request-timeout rather than
 * a clear, fast error. This check fails in a couple of seconds instead,
 * with a message that actually says what's wrong and how to fix it.
 */
@Component
public class OllamaHealthChecker {

    private final RestClient restClient;

    public OllamaHealthChecker(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public void checkReachableOrThrow() {
        try {
            restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new OllamaUnavailableException(
                    "Ollama is not reachable. Check it's running (`ollama serve`) and the model is pulled (`ollama list`).",
                    e);
        }
    }
}