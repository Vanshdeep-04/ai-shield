package com.aishield.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.aishield.analysis.risk.RiskAssessment;
import com.aishield.analysis.risk.RiskLevel;
import com.aishield.history.AnalysisFeedback;
import com.aishield.message.MessageSource;

/** One row of the history list. */
public record HistoryItem(
        UUID id,
        Instant createdAt,
        MessageSource source,
        String senderDomain,
        String subjectPreview,
        RiskLevel riskLevel,
        Integer riskScore,
        RiskAssessment.AiStatus aiStatus,
        AnalysisFeedback.Verdict feedbackVerdict) {
}
