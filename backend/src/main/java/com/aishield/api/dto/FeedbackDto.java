package com.aishield.api.dto;

import java.time.Instant;

import com.aishield.history.AnalysisFeedback;

public record FeedbackDto(AnalysisFeedback.Verdict verdict, String comment, Instant updatedAt) {

    public static FeedbackDto from(AnalysisFeedback f) {
        return new FeedbackDto(f.getVerdict(), f.getComment(), f.getUpdatedAt());
    }
}
