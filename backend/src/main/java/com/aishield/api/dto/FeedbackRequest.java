package com.aishield.api.dto;

import com.aishield.history.AnalysisFeedback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotNull(message = "verdict is required") AnalysisFeedback.Verdict verdict,
        @Size(max = 500, message = "comment must be at most 500 characters") String comment) {
}
