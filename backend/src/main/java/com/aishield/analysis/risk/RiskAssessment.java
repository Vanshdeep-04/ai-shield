package com.aishield.analysis.risk;

import java.util.List;

/**
 * The final, combined assessment.
 *
 * @param level             final risk level
 * @param score             final score 0-100, or {@code null} when the level is UNKNOWN
 * @param aiStatus          whether the AI content analysis contributed
 * @param explanation       plain-language summary combining all evidence
 * @param recommendedAction what the user should do
 * @param components        the individual component scores used in the calculation
 * @param evidence          evidence items, each labelled as observed fact or AI interpretation
 * @param limitations       what could not be checked or may reduce reliability
 */
public record RiskAssessment(
        RiskLevel level,
        Integer score,
        AiStatus aiStatus,
        String explanation,
        String recommendedAction,
        ComponentScores components,
        List<Evidence> evidence,
        List<String> limitations) {

    public RiskAssessment {
        evidence = List.copyOf(evidence);
        limitations = List.copyOf(limitations);
    }

    public enum AiStatus {
        /** Gemini returned a valid assessment. */
        COMPLETED,
        /** Gemini returned a valid response but judged the content impossible to assess. */
        INCONCLUSIVE,
        /** Gemini could not be used (not configured, quota, timeout, invalid response...). */
        UNAVAILABLE
    }

    /** Where a piece of evidence came from. */
    public enum EvidenceSource {
        AI_CONTENT_ANALYSIS, URL_ANALYSIS, SENDER_METADATA
    }

    /** Whether evidence is a deterministic observation or the AI model's interpretation. */
    public enum EvidenceType {
        OBSERVED, AI_INTERPRETATION
    }

    public record Evidence(EvidenceSource source, EvidenceType type, String code, String description, String detail) {
    }

    /**
     * @param ai       Gemini content score, {@code null} if unavailable
     * @param url      URL component score
     * @param metadata sender metadata component score
     */
    public record ComponentScores(Integer ai, int url, int metadata) {
    }
}
