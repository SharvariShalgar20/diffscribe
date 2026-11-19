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
    void errorStatusBecomesAnExceptionThatNeverContainsTheKey() {
        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid key\"}"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> provider.generate("", "hello"));

        assertTrue(e.getMessage().contains("401"));
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