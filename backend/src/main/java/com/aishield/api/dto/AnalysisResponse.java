package com.aishield.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aishield.analysis.gemini.GeminiAssessment;
import com.aishield.analysis.metadata.MetadataAnalysisService;
import com.aishield.analysis.metadata.MetadataIndicator;
import com.aishield.analysis.risk.RiskAssessment;
import com.aishield.analysis.risk.RiskLevel;
import com.aishield.analysis.risk.RiskAssessmentService.AiOutcome;
import com.aishield.analysis.url.UrlAnalysisResult;
import com.aishield.analysis.url.UrlFinding;
import com.aishield.analysis.url.UrlIndicator;
import com.aishield.message.MessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Full analysis result returned to the app (and stored, without the email body, as history detail).
 */
public record AnalysisResponse(
        UUID id,
        Instant createdAt,
        boolean saved,
        MessageSource source,
        MessageSummary message,
        RiskLevel riskLevel,
        Integer riskScore,
        String explanation,
        String recommendedAction,
        AiAnalysis ai,
        UrlAnalysis urlAnalysis,
        SenderChecks senderChecks,
        RiskAssessment.ComponentScores componentScores,
        List<RiskAssessment.Evidence> evidence,
        List<String> limitations,
        FeedbackDto feedback) {

    /** Minimal identification of the analysed message: sender domain and a short subject preview. */
    public record MessageSummary(String senderDomain, String subjectPreview) {
    }

    /**
     * The AI content assessment as shown to users. Deliberately contains no AI model or provider name;
     * the model is recorded only server-side (analysis_record.ai_model). Older stored results that
     * still contain "model"/"label" fields are read without them (unknown JSON fields are ignored).
     */
    public record AiAnalysis(
            RiskAssessment.AiStatus status,
            RiskLevel riskLevel,
            Integer riskScore,
            @JsonProperty("isPhishing") Boolean phishing,
            String explanation,
            List<GeminiAssessment.AiIndicator> indicators,
            List<String> benignFactors,
            String recommendedAction,
            String unavailableReason) {

        public static AiAnalysis from(AiOutcome outcome, RiskAssessment.AiStatus status) {
            GeminiAssessment a = outcome.assessment();
            if (a == null) {
                return new AiAnalysis(status, null, null, null, null, List.of(), List.of(), null,
                        outcome.failure() == null ? null : outcome.failure().userMessage());
            }
            return new AiAnalysis(status, a.riskLevel(), a.riskScore(), a.phishing(), a.explanation(),
                    a.indicators(), a.benignFactors(), a.recommendedAction(), null);
        }
    }

    public record UrlAnalysis(int score, int linksChecked, List<UrlFindingDto> findings, List<String> unavailableChecks) {

        public static UrlAnalysis from(UrlAnalysisResult r) {
            List<UrlFindingDto> findings = r.findings().stream().map(UrlFindingDto::from).toList();
            return new UrlAnalysis(r.score(), findings.size(), findings, r.unavailableChecks());
        }
    }

    public record UrlFindingDto(String url, String host, String registrableDomain, String displayText, int score,
                                List<IndicatorDto> indicators) {

        static UrlFindingDto from(UrlFinding f) {
            List<IndicatorDto> inds = f.indicators().stream().map(IndicatorDto::of).toList();
            return new UrlFindingDto(f.url(), f.host(), f.registrableDomain(), f.displayText(), f.score(), inds);
        }
    }

    public record SenderChecks(int score, boolean senderAvailable, List<IndicatorDto> findings) {

        public static SenderChecks from(MetadataAnalysisService.Result r) {
            return new SenderChecks(r.score(), r.senderAvailable(), r.indicators().stream().map(IndicatorDto::of).toList());
        }
    }

    public record IndicatorDto(String code, String description, int weight) {

        static IndicatorDto of(UrlIndicator i) {
            return new IndicatorDto(i.name(), i.description(), i.weight());
        }

        static IndicatorDto of(MetadataIndicator i) {
            return new IndicatorDto(i.name(), i.description(), i.weight());
        }
    }

    public AnalysisResponse withSaved(boolean savedFlag, UUID newId) {
        return new AnalysisResponse(newId, createdAt, savedFlag, source, message, riskLevel, riskScore, explanation,
                recommendedAction, ai, urlAnalysis, senderChecks, componentScores, evidence, limitations, feedback);
    }

    public AnalysisResponse withFeedback(FeedbackDto fb) {
        return new AnalysisResponse(id, createdAt, saved, source, message, riskLevel, riskScore, explanation,
                recommendedAction, ai, urlAnalysis, senderChecks, componentScores, evidence, limitations, fb);
    }
}
