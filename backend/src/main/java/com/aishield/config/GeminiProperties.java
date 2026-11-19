package com.aishield.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gemini Developer API settings. The API key is read from the {@code GEMINI_API_KEY}
 * environment variable and is never exposed to clients or written to logs.
 */
@ConfigurationProperties(prefix = "aishield.gemini")
public record GeminiProperties(
        String apiKey,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
        String apiRevision,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("1") int maxRetries,
        @DefaultValue("2s") Duration retryBackoff,
        @DefaultValue("15000") int maxInputChars,
        @DefaultValue("4096") int maxOutputTokens) {

    public GeminiProperties {
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("aishield.gemini.max-retries must be between 0 and 3");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never include the key in toString output (e.g. in logs or actuator dumps). */
    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + ", baseUrl=" + baseUrl + ", configured=" + isConfigured() + "]";
    }
}
