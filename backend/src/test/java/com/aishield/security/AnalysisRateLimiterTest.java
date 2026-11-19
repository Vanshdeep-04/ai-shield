package com.aishield.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishield.config.AppProperties;
import com.aishield.error.TooManyRequestsException;

class AnalysisRateLimiterTest {

    private static AppProperties props(int rpm) {
        return new AppProperties(600_000, rpm, new AppProperties.Cors(List.of()), new AppProperties.Security("dev", List.of()));
    }

    @Test
    void allowsUpToLimitThenRejectsPerUser() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T10:00:05Z"), ZoneOffset.UTC);
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(props(2), clock);
        limiter.check("a");
        limiter.check("a");
        assertThatThrownBy(() -> limiter.check("a")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.check("b")).doesNotThrowAnyException();
    }

    @Test
    void resetsInNextMinute() {
        AnalysisRateLimiter first = new AnalysisRateLimiter(props(1), Clock.fixed(Instant.parse("2026-11-19T10:00:05Z"), ZoneOffset.UTC));
        first.check("a");
        assertThatThrownBy(() -> first.check("a")).isInstanceOf(TooManyRequestsException.class);
    }
}
