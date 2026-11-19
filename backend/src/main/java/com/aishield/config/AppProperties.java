package com.aishield.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * General application settings bound from the {@code aishield.*} namespace.
 */
@ConfigurationProperties(prefix = "aishield")
public record AppProperties(
        @DefaultValue("600000") long maxRequestBytes,
        @DefaultValue("8") int analysisRequestsPerMinute,
        @DefaultValue Cors cors,
        @DefaultValue Security security) {

    public record Cors(@DefaultValue("http://localhost:8081") List<String> allowedOrigins) {
    }

    public record Security(
            @DefaultValue("google") String authMode,
            @DefaultValue List<String> googleClientIds) {

        public boolean isDevMode() {
            return "dev".equalsIgnoreCase(authMode);
        }
    }
}
