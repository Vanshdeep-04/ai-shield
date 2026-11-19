package com.aishield.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aishield.analysis.gemini.AiIndicatorCategory;
import com.aishield.analysis.gemini.GeminiAnalysisService;
import com.aishield.analysis.gemini.GeminiAssessment;
import com.aishield.analysis.risk.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Optional test against a REAL MySQL server (Flyway migration + history + feedback).
 * Skipped unless AISHIELD_IT_DB_URL is set, e.g.
 * <pre>
 * $env:AISHIELD_IT_DB_URL="jdbc:mysql://localhost:3306/aishield?serverTimezone=UTC&amp;allowPublicKeyRetrieval=true&amp;useSSL=false"
 * $env:AISHIELD_IT_DB_USERNAME="aishield"; $env:AISHIELD_IT_DB_PASSWORD="..."
 * mvn "-Dmaven.repo.local=../.tools/m2" test "-Dtest=MySqlIntegrationTest"
 * </pre>
 * It only deletes rows it created (its own test user ID).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "AISHIELD_IT_DB_URL", matches = "jdbc:mysql:.*")
class MySqlIntegrationTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AISHIELD_IT_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("AISHIELD_IT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("AISHIELD_IT_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    GeminiAnalysisService gemini;

    @Test
    void historyAndFeedbackWorkOnRealMySql() throws Exception {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isGreaterThanOrEqualTo(1);

        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.model()).thenReturn("mock-model");
        when(gemini.analyze(any())).thenReturn(new GeminiAnalysisService.Result(new GeminiAssessment(
                RiskLevel.HIGH, 90, true, "Synthetic explanation with unicode ₹ café 😀.",
                List.of(new GeminiAssessment.AiIndicator(AiIndicatorCategory.CREDENTIAL_REQUEST, "Asks for password", "your password")),
                List.of(), "Do not respond.", "mock-model"), false));

        // dev auth mode => user "dev:demo-user"; clean only this test user's rows
        mvc.perform(delete("/api/v1/analyses")).andExpect(status().isOk());

        String json = mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content(AnalysisApiIntegrationTest.PHISHING_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(json).get("id").asText();

        mvc.perform(put("/api/v1/analyses/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"ACCURATE\",\"comment\":\"verified on MySQL\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/analyses/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.ai.explanation").value("Synthetic explanation with unicode ₹ café 😀."))
                .andExpect(jsonPath("$.feedback.verdict").value("ACCURATE"));

        mvc.perform(get("/api/v1/analyses")).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].feedbackVerdict").value("ACCURATE"));

        // ON DELETE CASCADE removes the feedback with the analysis
        mvc.perform(delete("/api/v1/analyses/" + id)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_feedback WHERE analysis_id = ?", Integer.class, id)).isZero();
    }
}
