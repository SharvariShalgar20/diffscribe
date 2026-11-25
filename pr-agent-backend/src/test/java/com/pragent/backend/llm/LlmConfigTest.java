package com.pragent.backend.llm;

import com.pragent.backend.ollama.OllamaHealthChecker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LlmConfigTest {

    @Configuration
    @EnableConfigurationProperties({LlmProperties.class, LlmFallbackProperties.class})
    static class PropsConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class, LlmConfig.class)
            .withBean(ChatClient.class, () -> mock(ChatClient.class))
            .withBean(OllamaHealthChecker.class, () -> mock(OllamaHealthChecker.class));

    @Test
    void defaultsToOllamaWrappedInRetry() {
        runner.run(ctx -> assertThat(ctx.getBean(LlmProvider.class)).isInstanceOf(RetryingLlmProvider.class));
    }

    @Test
    void cloudProviderWithKeyStartsUp() {
        runner.withPropertyValues("pragent.llm.provider=gemini", "pragent.llm.api-key=k")
                .run(ctx -> assertThat(ctx.getBean(LlmProvider.class)).isInstanceOf(RetryingLlmProvider.class));
    }

    @Test
    void cloudProviderWithoutKeyStopsStartupWithAClearMessage() {
        runner.withPropertyValues("pragent.llm.provider=gemini")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasStackTraceContaining("LLM_API_KEY");
                });
    }

    @Test
    void configuredFallbackWrapsThePrimary() {
        runner.withPropertyValues("pragent.llm.provider=gemini", "pragent.llm.api-key=k",
                        "pragent.llm.fallback.provider=ollama")
                .run(ctx -> assertThat(ctx.getBean(LlmProvider.class)).isInstanceOf(FallbackLlmProvider.class));
    }

    @Test
    void fallbackWithoutItsKeyStopsStartup() {
        runner.withPropertyValues("pragent.llm.fallback.provider=groq")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}