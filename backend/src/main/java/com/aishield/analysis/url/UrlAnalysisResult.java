package com.aishield.analysis.url;

import java.util.List;

/**
 * Aggregate URL analysis for a message.
 *
 * @param findings          per-link results, ordered by score (highest first)
 * @param score             URL component score 0-100: the highest single-link score
 *                          (the worst link drives the risk; repeated links are not double-counted)
 * @param unavailableChecks checks that were deliberately not performed, reported to the user
 */
public record UrlAnalysisResult(List<UrlFinding> findings, int score, List<String> unavailableChecks) {

    public static final List<String> UNAVAILABLE_CHECKS = List.of(
            "Domain reputation / blocklist lookup: no threat-intelligence service is configured.",
            "Redirect resolution: links are never opened or followed, so final destinations are unknown.",
            "Domain age / WHOIS: not queried.");

    public UrlAnalysisResult {
        findings = List.copyOf(findings);
        unavailableChecks = List.copyOf(unavailableChecks);
    }

    public boolean hasLinks() {
        return !findings.isEmpty();
    }
}
