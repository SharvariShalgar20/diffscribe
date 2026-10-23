package com.pragent.backend.controller;

import com.pragent.backend.dto.ChangeType;
import com.pragent.backend.dto.PrDescription;
import com.pragent.backend.service.PrDescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrGenerationControllerTest {

    private PrDescriptionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PrDescriptionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PrGenerationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/api/generate-pr-description")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void emptyChunkListIsABadRequest() throws Exception {
        send("{\"diffChunks\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void blankChunkIsABadRequest() throws Exception {
        send("{\"diffChunks\":[\"real diff\",\"   \"]}").andExpect(status().isBadRequest());
    }

    @Test
    void validRequestReturns200() throws Exception {
        when(service.generate(any())).thenReturn(new PrDescription("feat: x", "body", ChangeType.FEAT));
        send("{\"diffChunks\":[\"real diff\"]}").andExpect(status().isOk());
    }
}