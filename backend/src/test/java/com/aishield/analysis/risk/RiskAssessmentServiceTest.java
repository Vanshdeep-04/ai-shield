package com.aishield.analysis.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishield.analysis.gemini.AiIndicatorCategory;
import com.aishield.analysis.gemini.GeminiAssessment;
import com.aishield.analysis.gemini.GeminiException;
import com.aishield.analysis.metadata.MetadataAnalysisService;
import com.aishield.analysis.metadata.MetadataIndicator;
import com.aishield.analysis.risk.RiskAssessmentService.AiOutcome;
import com.aishield.analysis.url.UrlAnalysisResult;
import com.aishield.analysis.url.UrlFinding;
import com.aishield.analysis.url.UrlIndicator;

class RiskAssessmentServiceTest {

    private final RiskAssessmentService engine = new RiskAssessmentService();

    private static GeminiAssessment ai(RiskLevel level, int score, boolean phishing) {
        return new GeminiAssessment(level, score, phishing, "AI explanation.",
                List.of(new GeminiAssessment.AiIndicator(AiIndicatorCategory.URGENCY_OR_THREAT, "Urgent tone", "act now")),
                List.of(), "AI action", "test-model");
    }

    private static UrlAnalysisResult urls(int... scores) {
        List<UrlFinding> findings = new java.util.ArrayList<>();
        for (int s : scores) {
            List<UrlIndicator> inds = s == 0 ? List.of() : List.of(UrlIndicator.IP_ADDRESS_HOST);
            findings.add(new UrlFinding("http://h" + s, "h" + s + ".example", "example", null, inds, s));
        }
        findings.sort((a, b) -> b.score() - a.score());
        int max = findings.stream().mapToInt(UrlFinding::score).max().orElse(0);
        return new UrlAnalysisResult(findings, max, UrlAnalysisResult.UNAVAILABLE_CHECKS);
    }

    private static MetadataAnalysisService.Result meta(MetadataIndicator... inds) {
        int score = Math.min(100, java.util.Arrays.stream(inds).mapToInt(MetadataIndicator::weight).sum());
        return new MetadataAnalysisService.Result(List.of(inds), score, true);
    }

    @Test
    void lowAiAndNoOtherEvidenceIsLow() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
        assertThat(r.score()).isEqualTo(10);
        assertThat(r.aiStatus()).isEqualTo(RiskAssessment.AiStatus.COMPLETED);
        assertThat(r.explanation()).contains("LOW").contains("No web links");
    }

    @Test
    void highAiIsHigh() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.HIGH, 85, true), false), urls(0), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.score()).isEqualTo(85);
    }

    @Test
    void strongUrlEvidenceRaisesRiskIndependentlyOfLanguage() {
        // AI sees nothing suspicious in the wording, but a link is structurally very suspicious.
        RiskAssessment withoutUrl = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(), meta());
        RiskAssessment withUrl = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(90), meta());
        assertThat(withUrl.score()).isGreaterThan(withoutUrl.score());
        // 1 - 0.9 * (1 - 0.72) = 0.748
        assertThat(withUrl.score()).isEqualTo(75);
        assertThat(withUrl.level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void weakUrlSignalAloneDoesNotMakeEmailHigh() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(15), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void manyLinksAreNotDoubleCounted() {
        RiskAssessment one = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(35), meta());
        RiskAssessment many = engine.assess(AiOutcome.success(ai(RiskLevel.LOW, 10, false), false), urls(35, 35, 35, 35), meta());
        assertThat(many.score()).isEqualTo(one.score());
    }

    @Test
    void metadataEvidenceContributes() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.MEDIUM, 50, false), false), urls(),
                meta(MetadataIndicator.DISPLAY_NAME_BRAND_MISMATCH, MetadataIndicator.REPLY_TO_DOMAIN_MISMATCH));
        assertThat(r.score()).isGreaterThan(50);
        assertThat(r.components().metadata()).isEqualTo(50);
        assertThat(r.evidence()).anyMatch(e -> e.source() == RiskAssessment.EvidenceSource.SENDER_METADATA
                && e.type() == RiskAssessment.EvidenceType.OBSERVED);
    }

    @Test
    void aiUnavailableWithWeakEvidenceIsUnknownNeverLow() {
        RiskAssessment r = engine.assess(AiOutcome.failure(GeminiException.Reason.TIMEOUT), urls(15), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(r.score()).isNull();
        assertThat(r.aiStatus()).isEqualTo(RiskAssessment.AiStatus.UNAVAILABLE);
        assertThat(r.limitations()).anyMatch(l -> l.contains("did not respond in time"));
        assertThat(r.recommendedAction()).contains("could not finish");
    }

    @Test
    void aiUnavailableWithNoEvidenceIsUnknown() {
        RiskAssessment r = engine.assess(AiOutcome.failure(GeminiException.Reason.NOT_CONFIGURED), urls(), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(r.explanation()).contains("could not complete");
    }

    @Test
    void aiUnavailableButStrongDeterministicEvidenceIsHigh() {
        RiskAssessment r = engine.assess(AiOutcome.failure(GeminiException.Reason.QUOTA_EXHAUSTED), urls(100),
                meta(MetadataIndicator.DISPLAY_NAME_BRAND_MISMATCH));
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.limitations()).anyMatch(l -> l.contains("link and sender checks only"));
    }

    @Test
    void aiInconclusiveIsTreatedLikeUnavailableForScoring() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.UNKNOWN, 0, false), false), urls(), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(r.aiStatus()).isEqualTo(RiskAssessment.AiStatus.INCONCLUSIVE);
    }

    @Test
    void inconsistentAiOutputIsReportedAsLimitation() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.HIGH, 20, true), false), urls(), meta());
        assertThat(r.level()).isEqualTo(RiskLevel.LOW); // the score wins over the label
        assertThat(r.limitations()).anyMatch(l -> l.contains("disagreed"));
        assertThat(r.limitations()).anyMatch(l -> l.contains("not fully consistent"));
    }

    @Test
    void evidenceIsLabelledByOrigin() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.MEDIUM, 45, false), true), urls(35), meta());
        assertThat(r.evidence()).anyMatch(e -> e.source() == RiskAssessment.EvidenceSource.AI_CONTENT_ANALYSIS
                && e.type() == RiskAssessment.EvidenceType.AI_INTERPRETATION);
        assertThat(r.evidence()).anyMatch(e -> e.source() == RiskAssessment.EvidenceSource.URL_ANALYSIS
                && e.type() == RiskAssessment.EvidenceType.OBSERVED);
        assertThat(r.limitations()).anyMatch(l -> l.contains("only the first part"));
        assertThat(r.limitations()).contains(RiskAssessmentService.SCORE_DISCLAIMER);
    }

    @Test
    void scoresStayWithinBounds() {
        RiskAssessment r = engine.assess(AiOutcome.success(ai(RiskLevel.HIGH, 100, true), false), urls(100),
                meta(MetadataIndicator.values()));
        assertThat(r.score()).isEqualTo(100);
        assertThat(RiskAssessmentService.toScore(-0.5)).isZero();
        assertThat(RiskAssessmentService.toScore(1.5)).isEqualTo(100);
    }

    @Test
    void bandsMatchDocumentation() {
        assertThat(RiskLevel.fromScore(39)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(40)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(69)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(70)).isEqualTo(RiskLevel.HIGH);
    }
}
