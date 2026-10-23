package com.pragent.backend.controller;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.dto.UpdatePrResponse;
import com.pragent.backend.github.GitHubAuthException;
import com.pragent.backend.github.GitHubRateLimitException;
import com.pragent.backend.ollama.OllamaUnavailableException;
import com.pragent.backend.service.PrUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrUpdateControllerTest {

    private static final String VALID_BODY =
            "{\"repoOwner\":\"o\",\"repoName\":\"r\",\"branch\":\"b\",\"diffChunks\":[\"diff\"]}";

    private PrUpdateService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PrUpdateService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PrUpdateController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/api/pr/update").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void missingBranchIsABadRequest() throws Exception {
        send("{\"repoOwner\":\"o\",\"repoName\":\"r\",\"diffChunks\":[\"diff\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyDiffChunksIsABadRequest() throws Exception {
        send("{\"repoOwner\":\"o\",\"repoName\":\"r\",\"branch\":\"b\",\"diffChunks\":[]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void successReturnsTheGeneratedDescriptionAndPrLink() throws Exception {
        when(service.updateExistingPr(any())).thenReturn(new UpdatePrResponse(
                new PrDescription("feat: x", "body", ChangeType.FEAT), 8, "https://github.com/o/r/pull/8"));

        String body = send(VALID_BODY).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"prNumber\":8"), body);
        assertTrue(body.contains("\"type\":\"feat\""), body);
    }

    @Test
    void missingPrMapsTo404() throws Exception {
        when(service.updateExistingPr(any())).thenThrow(new NoSuchElementException("no PR"));
        send(VALID_BODY).andExpect(status().isNotFound());
    }

    @Test
    void ollamaDownMapsTo503() throws Exception {
        when(service.updateExistingPr(any())).thenThrow(new OllamaUnavailableException("down", null));
        send(VALID_BODY).andExpect(status().isServiceUnavailable());
    }

    @Test
    void rateLimitMapsTo429() throws Exception {
        when(service.updateExistingPr(any())).thenThrow(new GitHubRateLimitException("slow down"));
        send(VALID_BODY).andExpect(status().isTooManyRequests());
    }

    @Test
    void githubAuthFailureMapsTo502() throws Exception {
        when(service.updateExistingPr(any())).thenThrow(new GitHubAuthException("bad token"));
        send(VALID_BODY).andExpect(status().isBadGateway());
    }
}