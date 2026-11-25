package com.pragent.backend.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.SocketTimeoutException;
import java.time.Duration;

class OpenAiCompatibleProviderTest {

    private static final String KEY = "secret-key";
    private MockRestServiceServer server;
    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(
                new LlmProperties("custom", KEY, "https://llm.test/v1", "test-model"), builder);
    }

    @Test
    void sendsSystemAndUserMessagesWithBearerKeyAndReturnsTheAnswer() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("be brief"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hello"))
                .andRespond(withSuccess(
                        "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hi there\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("hi there", provider.generate("be brief", "hello"));
        server.verify();
    }

    @Test
    void blankSystemPromptIsLeftOut() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("ok", provider.generate("", "hello"));
    }

    @Test
    void status401BecomesAnAuthExceptionThatNeverContainsTheKey() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid key\"}"));

        LlmAuthException e = assertThrows(LlmAuthException.class, () -> provider.generate("", "hello"));

        assertTrue(e.getMessage().contains("401"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void status429BecomesARateLimitExceptionWithRetryAfter() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));

        LlmRateLimitException e = assertThrows(LlmRateLimitException.class, () -> provider.generate("", "hello"));

        assertEquals(Duration.ofSeconds(7), e.retryAfter().orElseThrow());
    }

    @Test
    void status429WithoutHeaderHasNoRetryAfter() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        LlmRateLimitException e = assertThrows(LlmRateLimitException.class, () -> provider.generate("", "hello"));

        assertTrue(e.retryAfter().isEmpty());
    }

    @Test
    void status503BecomesUnavailable() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(LlmUnavailableException.class, () -> provider.generate("", "hello"));
    }

    @Test
    void timeoutBecomesUnavailable() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThrows(LlmUnavailableException.class, () -> provider.generate("", "hello"));
    }

    @Test
    void otherClientErrorsStayAGenericFailureWithTheStatus() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("model not found"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> provider.generate("", "hello"));

        assertTrue(e.getMessage().contains("400"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void emptyChoicesIsAnError() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> provider.generate("", "hello"));
    }

    @Test
    void availabilityCheckMakesNoHttpCall() {
        provider.checkAvailableOrThrow();

        server.verify(); // no expectations were set, so any call would have failed
    }
}