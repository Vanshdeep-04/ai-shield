package com.aishield.analysis;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.aishield.analysis.gemini.GeminiAnalysisService;
import com.aishield.analysis.gemini.GeminiException;
import com.aishield.analysis.metadata.MetadataAnalysisService;
import com.aishield.analysis.risk.RiskAssessment;
import com.aishield.analysis.risk.RiskAssessmentService;
import com.aishield.analysis.risk.RiskAssessmentService.AiOutcome;
import com.aishield.analysis.url.UrlAnalysisResult;
import com.aishield.analysis.url.UrlAnalysisService;
import com.aishield.api.dto.AnalysisRequest;
import com.aishield.api.dto.AnalysisResponse;
import com.aishield.error.BadRequestException;
import com.aishield.history.HistoryService;
import com.aishield.message.MessageNormalizer;
import com.aishield.message.NormalizedMessage;

/**
 * The analysis pipeline. It is independent of the message source: Gmail today, other sources later.
 * <pre>
 * request → normalise → URL analysis ┐
 *                    → sender checks ├→ risk engine → history → response
 *                    → Gemini        ┘
 * </pre>
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    static final int SUBJECT_PREVIEW_CHARS = 120;

    private final MessageNormalizer normalizer;
    private final UrlAnalysisService urlAnalysis;
    private final MetadataAnalysisService metadataAnalysis;
    private final GeminiAnalysisService gemini;
    private final RiskAssessmentService riskEngine;
    private final HistoryService history;
    private final Clock clock;

    public AnalysisService(MessageNormalizer normalizer, UrlAnalysisService urlAnalysis,
                           MetadataAnalysisService metadataAnalysis, GeminiAnalysisService gemini,
                           RiskAssessmentService riskEngine, HistoryService history, Clock clock) {
        this.normalizer = normalizer;
        this.urlAnalysis = urlAnalysis;
        this.metadataAnalysis = metadataAnalysis;
        this.gemini = gemini;
        this.riskEngine = riskEngine;
        this.history = history;
        this.clock = clock;
    }

    public AnalysisResponse analyze(String userId, AnalysisRequest request) {
        NormalizedMessage message = normalizer.normalize(request);
        if (message.bodyText().isBlank() && message.links().isEmpty()) {
            throw new BadRequestException("The message has no readable text to analyse.");
        }

        UrlAnalysisResult urls = urlAnalysis.analyze(message.links());
        MetadataAnalysisService.Result metadata = metadataAnalysis.analyze(message);
        AiOutcome ai = runAi(message);
        RiskAssessment assessment = riskEngine.assess(ai, urls, metadata);

        AnalysisResponse response = new AnalysisResponse(
                UUID.randomUUID(),
                clock.instant(),
                false,
                message.source(),
                new AnalysisResponse.MessageSummary(message.senderDomain(), preview(message.subject())),
                assessment.level(),
                assessment.score(),
                assessment.explanation(),
                assessment.recommendedAction(),
                AnalysisResponse.AiAnalysis.from(ai, assessment.aiStatus()),
                AnalysisResponse.UrlAnalysis.from(urls),
                AnalysisResponse.SenderChecks.from(metadata),
                assessment.components(),
                assessment.evidence(),
                assessment.limitations(),
                null);

        log.info("Analysis completed: source={} level={} aiStatus={} links={}",
                message.source(), assessment.level(), assessment.aiStatus(), message.links().size());

        try {
            AnalysisResponse saved = response.withSaved(true, response.id());
            history.save(userId, saved, ai.assessment() == null ? null : ai.assessment().model());
            return saved;
        } catch (DataAccessException | TransactionException | IllegalStateException e) {
            // The analysis is still useful to the user even if history storage is down.
            log.warn("Analysis result could not be saved to history: {}", e.getClass().getSimpleName());
            List<String> limitations = new ArrayList<>(response.limitations());
            limitations.add("This result could not be saved to your history (database unavailable).");
            return new AnalysisResponse(null, response.createdAt(), false, response.source(), response.message(),
                    response.riskLevel(), response.riskScore(), response.explanation(), response.recommendedAction(),
                    response.ai(), response.urlAnalysis(), response.senderChecks(), response.componentScores(),
                    response.evidence(), limitations, null);
        }
    }

    private AiOutcome runAi(NormalizedMessage message) {
        if (!gemini.isConfigured()) {
            return AiOutcome.failure(GeminiException.Reason.NOT_CONFIGURED);
        }
        try {
            GeminiAnalysisService.Result result = gemini.analyze(message);
            return AiOutcome.success(result.assessment(), result.inputTruncated());
        } catch (GeminiException e) {
            return AiOutcome.failure(e.reason());
        }
    }

    static String preview(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        return subject.length() > SUBJECT_PREVIEW_CHARS ? subject.substring(0, SUBJECT_PREVIEW_CHARS) + "…" : subject;
    }
}
