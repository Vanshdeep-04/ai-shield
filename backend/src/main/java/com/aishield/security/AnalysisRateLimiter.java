package com.aishield.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.aishield.config.AppProperties;
import com.aishield.error.TooManyRequestsException;

/**
 * Simple in-memory fixed-window limiter (per user, per minute) protecting the shared Gemini
 * free-tier quota. Suitable for a single backend instance.
 */
@Component
public class AnalysisRateLimiter {

    private final int limitPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AnalysisRateLimiter(AppProperties props, Clock clock) {
        this.limitPerMinute = props.analysisRequestsPerMinute();
        this.clock = clock;
    }

    public void check(String userId) {
        if (limitPerMinute <= 0) {
            return;
        }
        long minute = clock.millis() / 60_000;
        Window w = windows.compute(userId, (k, old) ->
                old == null || old.minute != minute ? new Window(minute, 1) : new Window(minute, old.count + 1));
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        }
        if (w.count > limitPerMinute) {
            throw new TooManyRequestsException("Too many analyses in the last minute. Please wait and try again.");
        }
    }

    private record Window(long minute, int count) {
    }
}
