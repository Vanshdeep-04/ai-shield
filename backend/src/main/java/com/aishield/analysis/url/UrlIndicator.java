package com.aishield.analysis.url;

/**
 * Structural URL indicators and their documented weights (points added to a URL's 0-100 score).
 *
 * <p>Weights express how strongly each indicator is associated with phishing links in common
 * security guidance. They are heuristics, not probabilities: no single weak indicator
 * (e.g. a URL shortener) can push a link into the HIGH band on its own. See docs/RISK_SCORING.md.</p>
 */
public enum UrlIndicator {

    LINK_TEXT_MISMATCH(40, "Visible link text shows a different domain than the real destination"),
    IP_ADDRESS_HOST(35, "Link points to a raw IP address instead of a domain name"),
    PUNYCODE_OR_UNICODE_HOST(35, "Domain uses punycode or non-ASCII characters that can imitate another domain"),
    USERINFO_IN_URL(30, "URL contains an '@' before the host, which can disguise the real destination"),
    DECEPTIVE_HOSTNAME(25, "Hostname contains a well-known brand name but is not that brand's domain"),
    MALFORMED_URL(20, "URL could not be parsed safely"),
    URL_SHORTENER(15, "Link uses a URL shortener that hides the final destination"),
    EXCESSIVE_SUBDOMAINS(15, "Hostname has an unusually deep chain of subdomains"),
    NON_STANDARD_PORT(15, "Link uses a non-standard network port"),
    EMBEDDED_REDIRECT(15, "URL contains another URL in its query, a common redirect trick"),
    SUSPICIOUS_CHARACTERS(10, "URL contains many hyphens or encoded characters"),
    LONG_URL(10, "URL is unusually long"),
    UNCOMMON_TLD(10, "Domain uses a top-level domain frequently seen in abuse reports"),
    INSECURE_HTTP(5, "Link does not use HTTPS");

    private final int weight;
    private final String description;

    UrlIndicator(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }
}
