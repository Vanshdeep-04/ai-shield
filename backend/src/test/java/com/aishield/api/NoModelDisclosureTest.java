package com.aishield.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aishield.analysis.gemini.AiIndicatorCategory;
import com.aishield.analysis.gemini.GeminiAnalysisService;
import com.aishield.analysis.gemini.GeminiAssessment;
import com.aishield.analysis.gemini.GeminiException;
import com.aishield.analysis.risk.RiskAssessment;
import com.aishield.analysis.risk.RiskLevel;
import com.aishield.history.AnalysisRecord;
import com.aishield.history.AnalysisRecordRepository;
import com.aishield.message.MessageSource;

/** The app must never reveal which AI model or provider is used. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoModelDisclosureTest {

    private static final String MODEL = "gemini-secret-model-x";

    @Autowired
    MockMvc mvc;
    @Autowired
    AnalysisRecordRepository records;
    @MockitoBean
    GeminiAnalysisService gemini;

    @BeforeEach
    void setUp() {
        records.deleteAll();
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.model()).thenReturn(MODEL);
    }

    private static void assertNoDisclosure(String body) {
        assertThat(body.toLowerCase()).doesNotContain("gemini").doesNotContain("flash").doesNotContain("secret-model");
        assertThat(body).doesNotContain("\"model\"").doesNotContain("aiModel");
    }

    @Test
    void analysisHistoryAndStatusResponsesContainNoModelOrProvider() throws Exception {
        when(gemini.analyze(any())).thenReturn(new GeminiAnalysisService.Result(new GeminiAssessment(RiskLevel.HIGH, 90, true,
                "Explanation.", List.of(new GeminiAssessment.AiIndicator(AiIndicatorCategory.OTHER, "d", "e")),
                List.of(), "Act.", MODEL), false));
        String analysis = mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                .content(AnalysisApiIntegrationTest.BENIGN_EMAIL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoDisclosure(analysis);

        String id = records.findAll().get(0).getId().toString();
        assertNoDisclosure(mvc.perform(get("/api/v1/analyses/" + id)).andReturn().getResponse().getContentAsString());
        assertNoDisclosure(mvc.perform(get("/api/v1/analyses")).andReturn().getResponse().getContentAsString());
        assertNoDisclosure(mvc.perform(get("/api/v1/status")).andReturn().getResponse().getContentAsString());
        // still recorded server-side for the project owner
        assertThat(records.findAll().get(0).getAiModel()).isEqualTo(MODEL);
    }

    @Test
    void failureMessagesContainNoModelOrProvider() throws Exception {
        for (GeminiException.Reason reason : GeminiException.Reason.values()) {
            assertNoDisclosure(reason.userMessage());
        }
        when(gemini.isConfigured()).thenReturn(false);
        assertNoDisclosure(mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                .content(AnalysisApiIntegrationTest.BENIGN_EMAIL)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void olderStoredResultsAreReturnedWithoutTheirModelFields() throws Exception {
        UUID id = UUID.randomUUID();
        String legacyJson = """
                {"id":"%s","createdAt":"2026-11-19T10:00:00Z","saved":true,"source":"MANUAL","message":{},
                 "riskLevel":"LOW","riskScore":5,"explanation":"x","recommendedAction":"y",
                 "ai":{"status":"COMPLETED","model":"gemini-3.5-flash-lite","label":"AI-generated interpretation (Gemini). It may be wrong.",
                       "riskLevel":"LOW","riskScore":5,"isPhishing":false,"explanation":"z","indicators":[],"benignFactors":[]},
                 "urlAnalysis":{"score":0,"linksChecked":0,"findings":[],"unavailableChecks":[]},
                 "senderChecks":{"score":0,"senderAvailable":false,"findings":[]},
                 "componentScores":{"ai":5,"url":0,"metadata":0},"evidence":[],"limitations":[]}
                """.formatted(id);
        records.save(new AnalysisRecord(id, "dev:demo-user", MessageSource.MANUAL, null, "legacy", RiskLevel.LOW, 5,
                RiskAssessment.AiStatus.COMPLETED, "gemini-3.5-flash-lite", legacyJson, Instant.parse("2026-11-19T10:00:00Z")));
        String body = mvc.perform(get("/api/v1/analyses/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoDisclosure(body);
    }
}
