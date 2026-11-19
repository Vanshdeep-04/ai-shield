package com.aishield.analysis.url;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishield.message.LinkRef;

class UrlAnalysisServiceTest {

    private final UrlAnalysisService service = new UrlAnalysisService();

    private UrlFinding check(String url) {
        return service.analyzeLink(new LinkRef(url, null));
    }

    private UrlFinding check(String url, String text) {
        return service.analyzeLink(new LinkRef(url, text));
    }

    @Test
    void ordinaryHttpsLinkHasNoIndicators() {
        UrlFinding f = check("https://accounts.google.com/signin");
        assertThat(f.indicators()).isEmpty();
        assertThat(f.score()).isZero();
        assertThat(f.host()).isEqualTo("accounts.google.com");
        assertThat(f.registrableDomain()).isEqualTo("google.com");
    }

    @Test
    void detectsIpAddressHosts() {
        assertThat(check("http://192.168.10.5/login").indicators()).contains(UrlIndicator.IP_ADDRESS_HOST);
        assertThat(check("http://3232235777/login").indicators()).contains(UrlIndicator.IP_ADDRESS_HOST);
        assertThat(check("http://[2001:db8::1]/x").indicators()).contains(UrlIndicator.IP_ADDRESS_HOST);
    }

    @Test
    void detectsUrlShorteners() {
        UrlFinding f = check("https://bit.ly/3abcDEF");
        assertThat(f.indicators()).containsExactly(UrlIndicator.URL_SHORTENER);
        assertThat(f.score()).isEqualTo(UrlIndicator.URL_SHORTENER.weight());
    }

    @Test
    void shortenerAloneStaysBelowMediumBand() {
        assertThat(check("https://bit.ly/x").score()).isLessThan(40);
    }

    @Test
    void detectsPunycodeAndUnicodeHosts() {
        assertThat(check("https://xn--pypal-4ve.com/").indicators()).contains(UrlIndicator.PUNYCODE_OR_UNICODE_HOST);
        assertThat(check("https://pаypal.com/").indicators()).contains(UrlIndicator.PUNYCODE_OR_UNICODE_HOST); // Cyrillic 'а'
    }

    @Test
    void detectsUserinfoTrick() {
        UrlFinding f = check("https://www.paypal.com@evil.example/login");
        assertThat(f.indicators()).contains(UrlIndicator.USERINFO_IN_URL);
        assertThat(f.host()).isEqualTo("evil.example");
    }

    @Test
    void detectsBrandNameInForeignDomain() {
        assertThat(check("https://paypal-account-security.com/verify").indicators()).contains(UrlIndicator.DECEPTIVE_HOSTNAME);
        assertThat(check("https://paypal.com.secure-login.net/").indicators()).contains(UrlIndicator.DECEPTIVE_HOSTNAME);
        assertThat(check("https://www.paypal.com/signin").indicators()).doesNotContain(UrlIndicator.DECEPTIVE_HOSTNAME);
    }

    @Test
    void detectsExcessiveSubdomainsLongUrlsAndPorts() {
        assertThat(check("https://a.b.c.d.example.com/").indicators()).contains(UrlIndicator.EXCESSIVE_SUBDOMAINS);
        assertThat(check("https://x.example.com/" + "a".repeat(120)).indicators()).contains(UrlIndicator.LONG_URL);
        assertThat(check("https://example.com:8443/").indicators()).contains(UrlIndicator.NON_STANDARD_PORT);
        assertThat(check("https://example.com:443/").indicators()).doesNotContain(UrlIndicator.NON_STANDARD_PORT);
    }

    @Test
    void detectsEmbeddedRedirectsAndSuspiciousCharacters() {
        assertThat(check("https://example.com/r?u=https://evil.example").indicators()).contains(UrlIndicator.EMBEDDED_REDIRECT);
        assertThat(check("https://secure-login-verify-account.com/").indicators()).contains(UrlIndicator.SUSPICIOUS_CHARACTERS);
    }

    @Test
    void detectsInsecureHttpAndUncommonTld() {
        UrlFinding f = check("http://free-prize.xyz/");
        assertThat(f.indicators()).contains(UrlIndicator.INSECURE_HTTP, UrlIndicator.UNCOMMON_TLD);
    }

    @Test
    void detectsLinkTextMismatch() {
        UrlFinding f = check("https://login-check.example.net/", "https://www.mybank.com");
        assertThat(f.indicators()).contains(UrlIndicator.LINK_TEXT_MISMATCH);
    }

    @Test
    void noMismatchWhenTextMatchesOrIsNotADomain() {
        assertThat(check("https://www.amazon.in/orders", "amazon.in").indicators()).doesNotContain(UrlIndicator.LINK_TEXT_MISMATCH);
        assertThat(check("https://example.com/files/1", "invoice.pdf").indicators()).doesNotContain(UrlIndicator.LINK_TEXT_MISMATCH);
        assertThat(check("https://example.com/", "Click here").indicators()).doesNotContain(UrlIndicator.LINK_TEXT_MISMATCH);
    }

    @Test
    void malformedUrlsAreFlaggedNotThrown() {
        assertThat(check("https://").indicators()).contains(UrlIndicator.MALFORMED_URL);
        assertThat(check("https://exa mple.com/").indicators()).contains(UrlIndicator.MALFORMED_URL);
        assertThat(check("https://example.com:abc/").indicators()).contains(UrlIndicator.MALFORMED_URL);
        assertThat(check("https://[::1/").indicators()).contains(UrlIndicator.MALFORMED_URL);
    }

    @Test
    void wwwPrefixIsTreatedAsHttp() {
        UrlFinding f = check("www.example.com/page");
        assertThat(f.host()).isEqualTo("www.example.com");
        assertThat(f.indicators()).containsExactly(UrlIndicator.INSECURE_HTTP);
    }

    @Test
    void scoreIsCappedAt100() {
        UrlFinding f = check("http://paypal.com@192.168.0.1:8080/" + "%41".repeat(10) + "?next=https://x.tk", "https://paypal.com");
        assertThat(f.score()).isEqualTo(100);
    }

    @Test
    void registrableDomainHandlesCountryCodeSuffixes() {
        assertThat(UrlAnalysisService.registrableDomain("www.bbc.co.uk")).isEqualTo("bbc.co.uk");
        assertThat(UrlAnalysisService.registrableDomain("mail.example.com")).isEqualTo("example.com");
        assertThat(UrlAnalysisService.registrableDomain("localhost")).isNull();
    }

    @Test
    void messageScoreIsWorstLinkNotSum() {
        UrlAnalysisResult r = service.analyze(List.of(
                new LinkRef("https://bit.ly/a", null),
                new LinkRef("https://bit.ly/b", null),
                new LinkRef("http://10.0.0.1/login", null)));
        assertThat(r.findings()).hasSize(3);
        assertThat(r.score()).isEqualTo(r.findings().get(0).score());
        assertThat(r.findings().get(0).host()).isEqualTo("10.0.0.1");
        assertThat(r.unavailableChecks()).isNotEmpty();
    }

    @Test
    void noLinksMeansZeroScore() {
        UrlAnalysisResult r = service.analyze(List.of());
        assertThat(r.score()).isZero();
        assertThat(r.hasLinks()).isFalse();
    }
}
