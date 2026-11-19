package com.aishield.analysis.risk;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aishield.analysis.gemini.GeminiAssessment;
import com.aishield.analysis.gemini.GeminiException;
import com.aishield.analysis.metadata.MetadataAnalysisService;
import com.aishield.analysis.metadata.MetadataIndicator;
import com.aishield.analysis.risk.RiskAssessment.AiStatus;
import com.aishield.analysis.risk.RiskAssessment.ComponentScores;
import com.aishield.analysis.risk.RiskAssessment.Evidence;
import com.aishield.analysis.risk.RiskAssessment.EvidenceSource;
import com.aishield.analysis.risk.RiskAssessment.EvidenceType;
import com.aishield.analysis.url.UrlAnalysisResult;
import com.aishield.analysis.url.UrlFinding;
import com.aishield.analysis.url.UrlIndicator;

/**
 * Combines the independent evidence sources into one transparent assessment.
 *
 * <h2>Scoring method (documented in docs/RISK_SCORING.md)</h2>
 * Each component produces a 0-100 score and is treated as an independent warning signal with a
 * reliability weight:
 * <ul>
 *   <li>AI content analysis (Gemini) — weight {@value #AI_WEIGHT}: reads the whole message, primary signal;</li>
 *   <li>URL structure — weight {@value #URL_WEIGHT}: deterministic, but structure alone cannot prove intent;</li>
 *   <li>Sender metadata — weight {@value #METADATA_WEIGHT}: deterministic, but headers are client-supplied and
 *       SPF/DKIM are not verified.</li>
 * </ul>
 * They are combined like independent alarms ("noisy-OR"):
 * {@code final = 100 × (1 − (1 − wA·A)(1 − wU·U)(1 − wM·M))}.
 * Any single strong signal raises the result on its own, several moderate signals reinforce each
 * other, and absent signals (score 0) never lower it. Double counting is avoided because Gemini is
 * told not to score URL structure, the URL component takes the worst single link rather than a sum
 * over links, and each component's indicators are distinct.
 *
 * <p>If the AI analysis is unavailable, the result is HIGH only when the deterministic evidence
 * alone reaches the HIGH threshold; otherwise it is UNKNOWN. It is never silently LOW.</p>
 */
@Service
public class RiskAssessmentService {

    static final double AI_WEIGHT = 1.0;
    static final double URL_WEIGHT = 0.8;
    static final double METADATA_WEIGHT = 0.6;

    static final String SCORE_DISCLAIMER =
            "The score is a heuristic risk indicator produced by AI Shield, not a guaranteed probability of phishing.";

    public RiskAssessment assess(AiOutcome ai, UrlAnalysisResult urls, MetadataAnalysisService.Result metadata) {
        List<Evidence> evidence = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        limitations.add(SCORE_DISCLAIMER);

        addUrlEvidence(urls, evidence);
        addMetadataEvidence(metadata, evidence);
        if (urls.hasLinks()) {
            limitations.addAll(urls.unavailableChecks());
        }
        limitations.add("Sender authentication (SPF/DKIM/DMARC) is not verified; a genuine-looking sender address can be forged.");
        if (!metadata.senderAvailable()) {
            limitations.add("No sender address was available, so sender checks were skipped.");
        }

        GeminiAssessment aiResult = ai.assessment();
        AiStatus aiStatus;
        Integer aiScore = null;
        if (aiResult == null) {
            aiStatus = AiStatus.UNAVAILABLE;
            limitations.add("AI content analysis was unavailable: " + ai.failure().userMessage());
        } else if (aiResult.riskLevel() == RiskLevel.UNKNOWN) {
            aiStatus = AiStatus.INCONCLUSIVE;
            limitations.add("The AI model could not judge the content (it may be too short or unreadable).");
            addAiEvidence(aiResult, evidence);
        } else {
            aiStatus = AiStatus.COMPLETED;
            aiScore = aiResult.riskScore();
            addAiEvidence(aiResult, evidence);
            noteAiInconsistencies(aiResult, limitations);
        }
        if (ai.inputTruncated()) {
            limitations.add("The email was long; only the first part was sent for AI analysis.");
        }

        double u = URL_WEIGHT * urls.score() / 100.0;
        double m = METADATA_WEIGHT * metadata.score() / 100.0;
        double deterministic = 1 - (1 - u) * (1 - m);
        ComponentScores components = new ComponentScores(aiScore, urls.score(), metadata.score());

        RiskLevel level;
        Integer score;
        if (aiScore != null) {
            double a = AI_WEIGHT * aiScore / 100.0;
            score = toScore(1 - (1 - a) * (1 - deterministic));
            level = RiskLevel.fromScore(score);
        } else {
            int deterministicScore = toScore(deterministic);
            if (deterministicScore >= RiskLevel.HIGH_THRESHOLD) {
                score = deterministicScore;
                level = RiskLevel.HIGH;
                limitations.add("The HIGH rating is based on link and sender checks only.");
            } else {
                score = null;
                level = RiskLevel.UNKNOWN;
            }
        }

        String explanation = explain(level, score, aiStatus, aiResult, urls, metadata);
        return new RiskAssessment(level, score, aiStatus, explanation, recommendedAction(level), components, evidence, limitations);
    }

    static int toScore(double combined) {
        return (int) Math.max(0, Math.min(100, Math.round(combined * 100)));
    }

    private static void addAiEvidence(GeminiAssessment ai, List<Evidence> evidence) {
        for (GeminiAssessment.AiIndicator ind : ai.indicators()) {
            evidence.add(new Evidence(EvidenceSource.AI_CONTENT_ANALYSIS, EvidenceType.AI_INTERPRETATION,
                    ind.category().name(), ind.description(), ind.evidence().isEmpty() ? null : ind.evidence()));
        }
    }

    private static void addUrlEvidence(UrlAnalysisResult urls, List<Evidence> evidence) {
        for (UrlFinding f : urls.findings()) {
            String where = f.host() != null ? f.host() : f.url();
            for (UrlIndicator ind : f.indicators()) {
                evidence.add(new Evidence(EvidenceSource.URL_ANALYSIS, EvidenceType.OBSERVED,
                        ind.name(), ind.description(), where));
            }
        }
    }

    private static void addMetadataEvidence(MetadataAnalysisService.Result metadata, List<Evidence> evidence) {
        for (MetadataIndicator ind : metadata.indicators()) {
            evidence.add(new Evidence(EvidenceSource.SENDER_METADATA, EvidenceType.OBSERVED,
                    ind.name(), ind.description(), null));
        }
    }

    private static void noteAiInconsistencies(GeminiAssessment ai, List<String> limitations) {
        if (RiskLevel.fromScore(ai.riskScore()) != ai.riskLevel()) {
            limitations.add("The AI's risk level and score disagreed; AI Shield used the score.");
        }
        boolean high = ai.riskScore() >= RiskLevel.HIGH_THRESHOLD;
        if (ai.phishing() != high) {
            limitations.add("The AI's phishing verdict and score were not fully consistent; treat the AI result with extra care.");
        }
    }

    private static String explain(RiskLevel level, Integer score, AiStatus aiStatus, GeminiAssessment ai,
                                  UrlAnalysisResult urls, MetadataAnalysisService.Result metadata) {
        StringBuilder sb = new StringBuilder();
        if (level == RiskLevel.UNKNOWN) {
            sb.append("AI Shield could not complete a reliable assessment of this email.");
        } else {
            sb.append("AI Shield rates this email ").append(level).append(" risk (score ").append(score).append("/100).");
        }

        if (aiStatus == AiStatus.COMPLETED || aiStatus == AiStatus.INCONCLUSIVE) {
            sb.append(" AI content analysis: ").append(ai.explanation());
        } else {
            sb.append(" The AI content analysis was not available.");
        }

        if (!urls.hasLinks()) {
            sb.append(" No web links were found.");
        } else {
            long flagged = urls.findings().stream().filter(f -> f.score() > 0 && hasMeaningfulIndicator(f)).count();
            if (flagged == 0) {
                sb.append(" Link checks: no structural warning signs in ").append(urls.findings().size())
                        .append(urls.findings().size() == 1 ? " link." : " links.");
            } else {
                UrlFinding worst = urls.findings().get(0);
                sb.append(" Link checks: ").append(flagged).append(" of ").append(urls.findings().size())
                        .append(urls.findings().size() == 1 ? " link shows" : " links show")
                        .append(" warning signs; strongest: ").append(lowerFirst(worst.indicators().get(0).description()))
                        .append(worst.host() != null ? " (" + worst.host() + ")." : ".");
            }
        }

        if (!metadata.indicators().isEmpty()) {
            sb.append(" Sender checks: ").append(lowerFirst(metadata.indicators().get(0).description())).append('.');
        }
        return sb.toString();
    }

    /** INSECURE_HTTP alone is too weak to describe a link as showing warning signs. */
    private static boolean hasMeaningfulIndicator(UrlFinding f) {
        return f.indicators().stream().anyMatch(i -> i != UrlIndicator.INSECURE_HTTP);
    }

    static String recommendedAction(RiskLevel level) {
        return switch (level) {
            case HIGH -> "Do not click any links, open attachments or reply. Never share passwords, OTPs or bank details. "
                    + "If the email claims to be from an organisation you use, contact it through its official app or website. "
                    + "Report it as phishing in Gmail and delete it.";
            case MEDIUM -> "Be cautious. Do not enter passwords or payment details through links in this email. "
                    + "Verify any request independently through official channels before acting.";
            case LOW -> "No strong warning signs were found. Stay alert: legitimate organisations never ask for passwords "
                    + "or OTPs by email.";
            case UNKNOWN -> "AI Shield could not finish the assessment. Treat the email with caution, verify any request "
                    + "through official channels, and try the analysis again later.";
        };
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * The outcome of the AI step: either an assessment or the reason it failed.
     */
    public record AiOutcome(GeminiAssessment assessment, GeminiException.Reason failure, boolean inputTruncated) {

        public static AiOutcome success(GeminiAssessment assessment, boolean inputTruncated) {
            return new AiOutcome(assessment, null, inputTruncated);
        }

        public static AiOutcome failure(GeminiException.Reason reason) {
            return new AiOutcome(null, reason, false);
        }
    }
}
