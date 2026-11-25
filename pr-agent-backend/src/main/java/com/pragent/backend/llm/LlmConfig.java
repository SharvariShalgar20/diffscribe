package com.pragent.backend.llm;

import com.pragent.backend.ollama.OllamaHealthChecker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    public LlmProvider llmProvider(LlmProperties primaryProps,
                                   LlmFallbackProperties fallbackProps,
                                   ObjectProvider<ChatClient> chatClient,
                                   ObjectProvider<OllamaHealthChecker> healthChecker,
                                   @Value("${pragent.llm.retry.max-attempts:3}") int maxAttempts,
                                   @Value("${pragent.llm.retry.max-wait-seconds:20}") long maxWaitSeconds) {
        LlmProvider primary = new RetryingLlmProvider(
                create(primaryProps, chatClient, healthChecker),
                maxAttempts, Duration.ofSeconds(maxWaitSeconds));

        return fallbackProps.toLlmProperties()
                .<LlmProvider>map(fp -> new FallbackLlmProvider(primary, create(fp, chatClient, healthChecker)))
                .orElse(primary);
    }

    private LlmProvider create(LlmProperties properties,
                               ObjectProvider<ChatClient> chatClient,
                               ObjectProvider<OllamaHealthChecker> healthChecker) {
        if (properties.isOllama()) {
            return new OllamaProvider(chatClient.getObject(), healthChecker.getObject());
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(120_000);
        return new OpenAiCompatibleProvider(properties, RestClient.builder().requestFactory(requestFactory));
    }
}