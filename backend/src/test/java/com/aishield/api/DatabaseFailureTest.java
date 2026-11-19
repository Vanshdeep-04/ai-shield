package com.aishield.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aishield.analysis.gemini.GeminiAnalysisService;
import com.aishield.history.HistoryService;

/** Database outages: history endpoints return 503; analysis still works but reports it was not saved. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseFailureTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    HistoryService history;

    @MockitoBean
    GeminiAnalysisService gemini;

    @Test
    void historyReturns503WithoutLeakingDetails() throws Exception {
        when(history.list(anyString(), anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("Communications link failure to localhost:3306, password=..."));
        String body = mvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Database unavailable"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("3306").doesNotContain("password");
    }

    @Test
    void analysisStillReturnsResultWhenHistoryCannotBeSaved() throws Exception {
        when(gemini.isConfigured()).thenReturn(false);
        doThrow(new DataAccessResourceFailureException("db down")).when(history).save(anyString(), any(), any());
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content(AnalysisApiIntegrationTest.BENIGN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.limitations[*]").isNotEmpty())
                .andExpect(jsonPath("$.riskLevel").value("UNKNOWN"));
    }
}
