package com.pragent.backend.llm;

import com.pragent.backend.ollama.OllamaHealthChecker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Bean
    public LlmProvider llmProvider(LlmProperties properties,
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