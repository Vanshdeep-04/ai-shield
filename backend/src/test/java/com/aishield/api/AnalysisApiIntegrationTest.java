package com.aishield.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.aishield.analysis.risk.RiskLevel;
import com.aishield.history.AnalysisRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Full HTTP → service → database tests with Gemini mocked (no API key, no network, no cost).
 * Uses synthetic emails only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisApiIntegrationTest {

    static final String PHISHING_EMAIL = """
            {"source":"GMAIL",
             "from":"\\"HDFC Bank Alerts\\" <alerts.hdfc@secure-mailer.example>",
             "replyTo":"verify@collect-desk.example",
             "subject":"URGENT: Your account will be suspended in 24 hours",
             "bodyText":"Dear customer, we detected unusual activity. Verify your account now or it will be blocked. Enter your OTP and password at the link below.",
             "bodyHtml":"<p>Dear customer, <a href=\\"http://192.168.4.20/hdfc/login\\">https://www.hdfcbank.com/login</a></p>",
             "receivedAt":"2026-11-18T10:15:00Z"}
            """;

    static final String BENIGN_EMAIL = """
            {"source":"GMAIL",
             "from":"Team Calendar <calendar@university.example>",
             "subject":"Reminder: project meeting on Friday",
             "bodyText":"Hi all, reminder that our project meeting is on Friday at 3pm in Room 204. Agenda: https://university.example/agenda"}
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AnalysisRecordRepository records;

    @MockitoBean
    GeminiAnalysisService gemini;

    @BeforeEach
    void setUp() {
        records.deleteAll();
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.model()).thenReturn("mock-model");
    }

    private static GeminiAnalysisService.Result aiResult(RiskLevel level, int score, boolean phishing) {
        return new GeminiAnalysisService.Result(new GeminiAssessment(level, score, phishing,
                "Synthetic AI explanation.",
                List.of(new GeminiAssessment.AiIndicator(AiIndicatorCategory.SENSITIVE_DATA_REQUEST, "Asks for OTP", "Enter your OTP")),
                List.of(), "Do not respond.", "mock-model"), false);
    }

    private JsonNode analyze(String body) throws Exception {
        String json = mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void phishingEmailIsRatedHighWithUrlAndSenderEvidence() throws Exception {
        when(gemini.analyze(any())).thenReturn(aiResult(RiskLevel.HIGH, 90, true));
        JsonNode r = analyze(PHISHING_EMAIL);

        assertThat(r.get("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(r.get("riskScore").asInt()).isGreaterThanOrEqualTo(90);
        assertThat(r.get("saved").asBoolean()).isTrue();
        assertThat(r.get("id").asText()).isNotBlank();
        assertThat(r.at("/ai/status").asText()).isEqualTo("COMPLETED");
        assertThat(r.at("/ai/isPhishing").asBoolean()).isTrue();
        assertThat(r.at("/urlAnalysis/findings/0/host").asText()).isEqualTo("192.168.4.20");
        assertThat(r.at("/urlAnalysis/findings/0/indicators").toString())
                .contains("IP_ADDRESS_HOST").contains("LINK_TEXT_MISMATCH");
        assertThat(r.at("/senderChecks/findings").toString())
                .contains("REPLY_TO_DOMAIN_MISMATCH").contains("DISPLAY_NAME_BRAND_MISMATCH");
        assertThat(r.at("/message/senderDomain").asText()).isEqualTo("secure-mailer.example");
        assertThat(r.toString()).doesNotContain("Enter your OTP and password at the link below"); // body is not echoed
        assertThat(r.get("limitations").toString()).contains("not a guaranteed probability");
    }

    @Test
    void urlIndicatorsRaiseRiskEvenWhenAiSeesLowRisk() throws Exception {
        when(gemini.analyze(any())).thenReturn(aiResult(RiskLevel.LOW, 15, false));
        JsonNode r = analyze(PHISHING_EMAIL);
        assertThat(r.at("/componentScores/ai").asInt()).isEqualTo(15);
        assertThat(r.get("riskScore").asInt()).isGreaterThan(15);
        assertThat(r.get("riskLevel").asText()).isEqualTo("HIGH");
    }

    @Test
    void benignEmailIsLow() throws Exception {
        when(gemini.analyze(any())).thenReturn(aiResult(RiskLevel.LOW, 5, false));
        JsonNode r = analyze(BENIGN_EMAIL);
        assertThat(r.get("riskLevel").asText()).isEqualTo("LOW");
        assertThat(r.at("/urlAnalysis/linksChecked").asInt()).isEqualTo(1);
        assertThat(r.at("/urlAnalysis/score").asInt()).isZero();
    }

    @Test
    void geminiFailureGivesUnknownNotLow() throws Exception {
        when(gemini.analyze(any())).thenThrow(new GeminiException(GeminiException.Reason.QUOTA_EXHAUSTED));
        JsonNode r = analyze(BENIGN_EMAIL);
        assertThat(r.get("riskLevel").asText()).isEqualTo("UNKNOWN");
        assertThat(r.get("riskScore")).isNull();
        assertThat(r.at("/ai/status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(r.at("/ai/unavailableReason").asText()).contains("quota");
    }

    @Test
    void missingApiKeyGivesUnknownWithExplanation() throws Exception {
        when(gemini.isConfigured()).thenReturn(false);
        JsonNode r = analyze(BENIGN_EMAIL);
        assertThat(r.get("riskLevel").asText()).isEqualTo("UNKNOWN");
        assertThat(r.at("/ai/unavailableReason").asText()).contains("not configured");
    }

    @Test
    void rejectsEmptyContent() throws Exception {
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"GMAIL\",\"subject\":\"hi\",\"bodyText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", hasItem(containsString("bodyText or bodyHtml"))));
    }

    @Test
    void rejectsMissingSourceAndOversizedFields() throws Exception {
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content("{\"bodyText\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("source")));
        String longSubject = "s".repeat(1001);
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"MANUAL\",\"bodyText\":\"x\",\"subject\":\"" + longSubject + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("subject")));
    }

    @Test
    void rejectsMalformedJsonAndUnknownSource() throws Exception {
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"SMS\",\"bodyText\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedRequestBody() throws Exception {
        String huge = "{\"source\":\"MANUAL\",\"bodyText\":\"" + "a".repeat(700_000) + "\"}";
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void historyFeedbackAndDeletionFlow() throws Exception {
        when(gemini.analyze(any())).thenReturn(aiResult(RiskLevel.HIGH, 90, true));
        String id = analyze(PHISHING_EMAIL).get("id").asText();
        analyze(BENIGN_EMAIL);

        mvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[1].id").value(id))
                .andExpect(jsonPath("$.items[1].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.items[1].senderDomain").value("secure-mailer.example"))
                .andExpect(jsonPath("$.items[1].feedbackVerdict").doesNotExist()); // nulls are omitted

        mvc.perform(put("/api/v1/analyses/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"ACCURATE\",\"comment\":\"Correct, this was phishing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("ACCURATE"));
        // re-submitting replaces the previous feedback
        mvc.perform(put("/api/v1/analyses/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"FALSE_POSITIVE\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/analyses/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.feedback.verdict").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.urlAnalysis.findings[0].host").value("192.168.4.20"));

        mvc.perform(get("/api/v1/analyses")).andExpect(jsonPath("$.items[1].feedbackVerdict").value("FALSE_POSITIVE"));

        mvc.perform(delete("/api/v1/analyses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/analyses/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/analyses")).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(1));
        mvc.perform(get("/api/v1/analyses")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void feedbackValidation() throws Exception {
        when(gemini.analyze(any())).thenReturn(aiResult(RiskLevel.LOW, 5, false));
        String id = analyze(BENIGN_EMAIL).get("id").asText();
        mvc.perform(put("/api/v1/analyses/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/analyses/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"ACCURATE\",\"comment\":\"" + "c".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/analyses/00000000-0000-0000-0000-000000000000/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verdict\":\"ACCURATE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/analyses/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void historyPageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/v1/analyses").param("size", "5000").param("page", "-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void publicStatusAndHealthEndpoints() throws Exception {
        mvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ai-shield"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/heapdump")) {
            int code = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isIn(401, 403, 404);
        }
    }
}
