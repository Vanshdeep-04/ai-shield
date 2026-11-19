package com.aishield.analysis.gemini;

import java.util.List;

import com.aishield.analysis.risk.RiskLevel;

/**
 * Gemini's validated content assessment. Everything here is AI-generated interpretation
 * and is labelled as such in API responses.
 *
 * @param riskLevel         AI risk level (may be UNKNOWN when the model is unsure)
 * @param riskScore         AI score 0-100
 * @param phishing          whether the model judged the email to be phishing/scam
 * @param explanation       short explanation
 * @param indicators        content indicators with quoted evidence
 * @param benignFactors     reasons the email may be legitimate
 * @param recommendedAction the model's suggested action
 * @param model             the model that produced the assessment
 */
public record GeminiAssessment(
        RiskLevel riskLevel,
        int riskScore,
        boolean phishing,
        String explanation,
        List<AiIndicator> indicators,
        List<String> benignFactors,
        String recommendedAction,
        String model) {

    public GeminiAssessment {
        indicators = List.copyOf(indicators);
        benignFactors = List.copyOf(benignFactors);
    }

    public record AiIndicator(AiIndicatorCategory category, String description, String evidence) {
    }
}
