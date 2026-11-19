package com.aishield.analysis.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.aishield.analysis.risk.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiResponseValidatorTest {

    private final GeminiResponseValidator validator = new GeminiResponseValidator(new ObjectMapper());

    static final String VALID = """
            {"riskLevel":"HIGH","riskScore":88,"isPhishing":true,
             "explanation":"The email asks for your password and threatens account closure.",
             "indicators":[{"category":"CREDENTIAL_REQUEST","description":"Asks for password","evidence":"confirm your password"},
                           {"category":"URGENCY_OR_THREAT","description":"Threat","evidence":"within 24 hours"}],
             "benignFactors":[],
             "recommendedAction":"Do not click the link."}
            """;

    @Test
    void acceptsValidResponse() {
        GeminiAssessment a = validator.validate(VALID, "test-model");
        assertThat(a.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(a.riskScore()).isEqualTo(88);
        assertThat(a.phishing()).isTrue();
        assertThat(a.indicators()).hasSize(2);
        assertThat(a.indicators().get(0).category()).isEqualTo(AiIndicatorCategory.CREDENTIAL_REQUEST);
        assertThat(a.model()).isEqualTo("test-model");
    }

    @Test
    void acceptsJsonWrappedInCodeFence() {
        assertThat(validator.validate("```json\n" + VALID + "\n```", "m").riskScore()).isEqualTo(88);
    }

    @Test
    void rejectsUnsupportedRiskLevel() {
        assertInvalid(VALID.replace("\"HIGH\"", "\"CRITICAL\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "-1", "55.5", "\"80\"", "null"})
    void rejectsInvalidScores(String score) {
        assertInvalid(VALID.replace("88", score));
    }

    @Test
    void acceptsWholeNumberWrittenAsDecimal() {
        assertThat(validator.validate(VALID.replace("88", "88.0"), "m").riskScore()).isEqualTo(88);
    }

    @ParameterizedTest
    @ValueSource(strings = {"riskLevel", "riskScore", "isPhishing", "explanation", "indicators", "benignFactors", "recommendedAction"})
    void rejectsMissingRequiredFields(String field) {
        assertInvalid(VALID.replace("\"" + field + "\"", "\"renamed_" + field + "\""));
    }

    @Test
    void rejectsWrongTypes() {
        assertInvalid(VALID.replace("\"isPhishing\":true", "\"isPhishing\":\"yes\""));
        assertInvalid(VALID.replace("\"benignFactors\":[]", "\"benignFactors\":\"none\""));
        assertInvalid(VALID.replace("\"explanation\":\"The email asks for your password and threatens account closure.\"", "\"explanation\":\"  \""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "{", "[]", "\"text\"", "{\"riskLevel\":\"LOW\"}"})
    void rejectsMalformedJson(String body) {
        assertInvalid(body);
    }

    @Test
    void rejectsNull() {
        assertInvalid(null);
    }

    @Test
    void mapsUnknownIndicatorCategoryToOther() {
        GeminiAssessment a = validator.validate(VALID.replace("CREDENTIAL_REQUEST", "SOMETHING_NEW"), "m");
        assertThat(a.indicators().get(0).category()).isEqualTo(AiIndicatorCategory.OTHER);
    }

    @Test
    void truncatesOverlongText() {
        String longText = "a".repeat(5000);
        GeminiAssessment a = validator.validate(VALID.replace("Do not click the link.", longText), "m");
        assertThat(a.recommendedAction().length()).isLessThanOrEqualTo(GeminiResponseValidator.MAX_ACTION + 1);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> validator.validate(json, "m"))
                .isInstanceOf(GeminiException.class)
                .extracting(e -> ((GeminiException) e).reason())
                .isEqualTo(GeminiException.Reason.MALFORMED_RESPONSE);
    }
}
