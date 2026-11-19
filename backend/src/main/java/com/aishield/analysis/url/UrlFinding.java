package com.aishield.analysis.url;

import java.util.List;

/**
 * Result of the structural checks on one link.
 *
 * @param url               the link (shortened for display if very long)
 * @param host              parsed hostname, or {@code null} if unparseable
 * @param registrableDomain approximate registrable domain (e.g. {@code example.co.uk})
 * @param displayText       visible link text, if any
 * @param indicators        indicators that matched
 * @param score             0-100, sum of indicator weights capped at 100
 */
public record UrlFinding(
        String url,
        String host,
        String registrableDomain,
        String displayText,
        List<UrlIndicator> indicators,
        int score) {

    public UrlFinding {
        indicators = List.copyOf(indicators);
    }
}
