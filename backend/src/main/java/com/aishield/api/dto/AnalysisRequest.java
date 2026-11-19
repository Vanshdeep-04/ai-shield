package com.aishield.api.dto;

import java.time.Instant;
import java.util.List;

import com.aishield.message.MessageSource;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A message submitted for analysis. The mobile app fills it from the selected Gmail message
 * (or from manual input). No OAuth token is ever part of this payload.
 */
public record AnalysisRequest(
        @NotNull(message = "source is required") MessageSource source,
        @Size(max = 512, message = "from must be at most 512 characters") String from,
        @Size(max = 512, message = "replyTo must be at most 512 characters") String replyTo,
        @Size(max = 1000, message = "subject must be at most 1000 characters") String subject,
        @Size(max = 100_000, message = "bodyText must be at most 100000 characters") String bodyText,
        @Size(max = 400_000, message = "bodyHtml must be at most 400000 characters") String bodyHtml,
        @Size(max = 300, message = "at most 300 links may be submitted") List<@Valid LinkDto> links,
        Instant receivedAt) {

    @JsonIgnore
    @AssertTrue(message = "bodyText or bodyHtml must contain message content")
    public boolean isContentPresent() {
        return (bodyText != null && !bodyText.isBlank()) || (bodyHtml != null && !bodyHtml.isBlank());
    }

    public record LinkDto(
            @NotBlank(message = "link url is required")
            @Size(max = 4096, message = "link url must be at most 4096 characters") String url,
            @Size(max = 1000, message = "link text must be at most 1000 characters") String text) {
    }
}
