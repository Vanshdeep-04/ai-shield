package com.aishield.analysis.url;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.aishield.message.LinkRef;

/**
 * Independent, deterministic structural analysis of links.
 *
 * <p>This component never opens, resolves or follows a URL. It only parses the text of the
 * link, so it is safe to run on malicious input and works without any paid service.
 * It is deliberately separate from the Gemini content analysis.</p>
 */
@Service
public class UrlAnalysisService {

    static final int LONG_URL_THRESHOLD = 100;
    static final int MAX_SUBDOMAIN_DEPTH = 3;
    private static final int MAX_DISPLAY_URL = 300;

    private static final Pattern WEB_URL = Pattern.compile("^(?i)(https?)://([^/?#]*)([^#]*)(#.*)?$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern NUMERIC_HOST = Pattern.compile("^(0x[0-9a-f]+|\\d{8,10})$");
    private static final Pattern HOST_LABEL = Pattern.compile("^[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?$");
    private static final Pattern DOMAIN_IN_TEXT = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24})(?![a-z0-9-])");
    private static final Pattern EMBEDDED_URL = Pattern.compile("(?i)(https?(:|%3a)(//|%2f%2f))");
    private static final Pattern PERCENT_ENCODED = Pattern.compile("%[0-9a-fA-F]{2}");

    /** Well-known URL shorteners. A shortener is a weak signal on its own. */
    static final Set<String> SHORTENERS = Set.of(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly",
            "rebrand.ly", "shorturl.at", "tiny.cc", "rb.gy", "s.id", "t.ly", "bl.ink", "v.gd",
            "shorte.st", "adf.ly", "lnkd.in", "qrco.de");

    /** TLDs that appear disproportionately in abuse reports. Weak signal; many legitimate sites use them. */
    static final Set<String> UNCOMMON_TLDS = Set.of(
            "zip", "mov", "xyz", "top", "click", "link", "gq", "tk", "ml", "cf", "ga", "work",
            "rest", "cam", "icu", "loan", "kim", "country", "support", "monster");

    /** Brand names commonly impersonated, mapped to the registrable domains they legitimately use. */
    static final List<Brand> BRANDS = List.of(
            new Brand("paypal", Set.of("paypal.com", "paypal.me")),
            new Brand("apple", Set.of("apple.com", "icloud.com")),
            new Brand("icloud", Set.of("icloud.com", "apple.com")),
            new Brand("microsoft", Set.of("microsoft.com", "microsoftonline.com", "live.com", "office.com")),
            new Brand("office365", Set.of("office.com", "microsoft.com")),
            new Brand("outlook", Set.of("outlook.com", "live.com", "office.com", "microsoft.com")),
            new Brand("google", Set.of("google.com", "googleusercontent.com", "googleapis.com", "gstatic.com", "google.co.in")),
            new Brand("gmail", Set.of("google.com", "gmail.com")),
            new Brand("amazon", Set.of("amazon.com", "amazon.in", "amazon.co.uk", "amazonaws.com", "amazon.de")),
            new Brand("netflix", Set.of("netflix.com")),
            new Brand("facebook", Set.of("facebook.com", "fb.com")),
            new Brand("instagram", Set.of("instagram.com")),
            new Brand("whatsapp", Set.of("whatsapp.com", "whatsapp.net")),
            new Brand("linkedin", Set.of("linkedin.com")),
            new Brand("dropbox", Set.of("dropbox.com", "dropboxusercontent.com")),
            new Brand("docusign", Set.of("docusign.com", "docusign.net")),
            new Brand("adobe", Set.of("adobe.com")),
            new Brand("dhl", Set.of("dhl.com", "dhl.de")),
            new Brand("fedex", Set.of("fedex.com")),
            new Brand("hdfcbank", Set.of("hdfcbank.com")),
            new Brand("icicibank", Set.of("icicibank.com")),
            new Brand("onlinesbi", Set.of("onlinesbi.sbi", "sbi.co.in")),
            new Brand("paytm", Set.of("paytm.com")),
            new Brand("binance", Set.of("binance.com")));

    /** TLDs accepted when link text names a domain without "http" or "www." (avoids treating "file.pdf" as a domain). */
    private static final Set<String> COMMON_TLDS = Set.of(
            "com", "net", "org", "in", "io", "co", "uk", "us", "edu", "gov", "info", "biz", "me", "de",
            "au", "ca", "app", "dev", "ai", "fr", "jp", "eu", "ru", "cn", "br", "nl", "it", "es", "sbi",
            "xyz", "top", "online", "site", "shop", "store", "live", "tk", "ml", "ga", "cf", "gq", "ly");

    /** Second-level labels that act like public suffixes under country-code TLDs (e.g. co.uk). */
    private static final Set<String> SECOND_LEVEL_SUFFIXES = Set.of(
            "co", "com", "net", "org", "gov", "edu", "ac", "gob", "nic", "ltd", "plc", "res", "gen", "firm", "ind");

    public UrlAnalysisResult analyze(List<LinkRef> links) {
        List<UrlFinding> findings = new ArrayList<>();
        for (LinkRef link : links) {
            findings.add(analyzeLink(link));
        }
        findings.sort(Comparator.comparingInt(UrlFinding::score).reversed());
        int score = findings.stream().mapToInt(UrlFinding::score).max().orElse(0);
        return new UrlAnalysisResult(findings, score, UrlAnalysisResult.UNAVAILABLE_CHECKS);
    }

    UrlFinding analyzeLink(LinkRef link) {
        String raw = link.url().trim();
        String url = raw.regionMatches(true, 0, "www.", 0, 4) ? "http://" + raw : raw;
        EnumSet<UrlIndicator> hits = EnumSet.noneOf(UrlIndicator.class);

        Matcher m = WEB_URL.matcher(url);
        if (!m.matches()) {
            hits.add(UrlIndicator.MALFORMED_URL);
            return finding(raw, null, null, link.displayText(), hits);
        }
        String scheme = m.group(1).toLowerCase(Locale.ROOT);
        String authority = m.group(2);
        String pathAndQuery = m.group(3) == null ? "" : m.group(3);

        if ("http".equals(scheme)) {
            hits.add(UrlIndicator.INSECURE_HTTP);
        }

        String hostPort = authority;
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            hits.add(UrlIndicator.USERINFO_IN_URL);
            hostPort = authority.substring(at + 1);
        }

        String host;
        String port = null;
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            if (close < 0) {
                hits.add(UrlIndicator.MALFORMED_URL);
                return finding(raw, null, null, link.displayText(), hits);
            }
            host = hostPort.substring(0, close + 1);
            if (close + 1 < hostPort.length() && hostPort.charAt(close + 1) == ':') {
                port = hostPort.substring(close + 2);
            }
            hits.add(UrlIndicator.IP_ADDRESS_HOST);
        } else {
            int colon = hostPort.lastIndexOf(':');
            if (colon >= 0) {
                port = hostPort.substring(colon + 1);
                hostPort = hostPort.substring(0, colon);
            }
            host = hostPort;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            hits.add(UrlIndicator.MALFORMED_URL);
            return finding(raw, null, null, link.displayText(), hits);
        }

        if (port != null && !port.isEmpty()) {
            boolean standard = ("http".equals(scheme) && "80".equals(port)) || ("https".equals(scheme) && "443".equals(port));
            if (!port.chars().allMatch(Character::isDigit)) {
                hits.add(UrlIndicator.MALFORMED_URL);
            } else if (!standard) {
                hits.add(UrlIndicator.NON_STANDARD_PORT);
            }
        }

        String registrable = null;
        boolean isIp = hits.contains(UrlIndicator.IP_ADDRESS_HOST) || IPV4.matcher(host).matches() || NUMERIC_HOST.matcher(host).matches();
        if (isIp) {
            hits.add(UrlIndicator.IP_ADDRESS_HOST);
        } else {
            if (!host.chars().allMatch(c -> c < 128) || host.contains("xn--")) {
                hits.add(UrlIndicator.PUNYCODE_OR_UNICODE_HOST);
            }
            String[] labels = host.split("\\.");
            boolean asciiLabelsValid = true;
            for (String label : labels) {
                if (label.chars().allMatch(c -> c < 128) && !HOST_LABEL.matcher(label).matches()) {
                    asciiLabelsValid = false;
                }
            }
            if (labels.length < 2 || !asciiLabelsValid) {
                hits.add(UrlIndicator.MALFORMED_URL);
            }
            registrable = registrableDomain(host);
            if (registrable != null) {
                int depth = labels.length - registrable.split("\\.").length;
                if (depth > MAX_SUBDOMAIN_DEPTH) {
                    hits.add(UrlIndicator.EXCESSIVE_SUBDOMAINS);
                }
                if (SHORTENERS.contains(host) || SHORTENERS.contains(registrable)) {
                    hits.add(UrlIndicator.URL_SHORTENER);
                }
                String tld = labels[labels.length - 1];
                if (UNCOMMON_TLDS.contains(tld)) {
                    hits.add(UrlIndicator.UNCOMMON_TLD);
                }
                if (impersonatesBrand(host, registrable)) {
                    hits.add(UrlIndicator.DECEPTIVE_HOSTNAME);
                }
            }
            if (countChar(host, '-') >= 3) {
                hits.add(UrlIndicator.SUSPICIOUS_CHARACTERS);
            }
        }

        if (PERCENT_ENCODED.matcher(pathAndQuery).results().count() >= 6) {
            hits.add(UrlIndicator.SUSPICIOUS_CHARACTERS);
        }
        if (EMBEDDED_URL.matcher(pathAndQuery).find()) {
            hits.add(UrlIndicator.EMBEDDED_REDIRECT);
        }
        if (raw.length() > LONG_URL_THRESHOLD) {
            hits.add(UrlIndicator.LONG_URL);
        }
        if (displayTextMismatch(link.displayText(), registrable, host)) {
            hits.add(UrlIndicator.LINK_TEXT_MISMATCH);
        }
        return finding(raw, host, registrable, link.displayText(), hits);
    }

    private static UrlFinding finding(String url, String host, String registrable, String text, EnumSet<UrlIndicator> hits) {
        int score = Math.min(100, hits.stream().mapToInt(UrlIndicator::weight).sum());
        String shown = url.length() > MAX_DISPLAY_URL ? url.substring(0, MAX_DISPLAY_URL) + "…" : url;
        String shownText = text != null && text.length() > 200 ? text.substring(0, 200) + "…" : text;
        List<UrlIndicator> ordered = new ArrayList<>(hits);
        ordered.sort(Comparator.comparingInt(UrlIndicator::weight).reversed());
        return new UrlFinding(shown, host, registrable, shownText, ordered, score);
    }

    /**
     * Approximates the registrable domain without the full Public Suffix List:
     * the last two labels, or last three when the second-level label is a known
     * country-code sub-suffix (e.g. {@code bbc.co.uk}). Documented limitation.
     */
    static String registrableDomain(String host) {
        if (host == null) {
            return null;
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        String tld = labels[labels.length - 1];
        String second = labels[labels.length - 2];
        if (labels.length >= 3 && tld.length() == 2 && SECOND_LEVEL_SUFFIXES.contains(second)) {
            return labels[labels.length - 3] + "." + second + "." + tld;
        }
        return second + "." + tld;
    }

    /** Public helper so other analysers (e.g. sender checks) use the same domain approximation. */
    public static String registrableDomainOf(String host) {
        return host == null ? null : registrableDomain(host.toLowerCase(Locale.ROOT));
    }

    /** True when {@code word} is exactly a known brand name and {@code registrable} is not one of its domains. */
    public static boolean isBrandOutsideItsDomains(String word, String registrable) {
        for (Brand brand : BRANDS) {
            if (brand.name().equals(word)) {
                return !brand.domains().contains(registrable);
            }
        }
        return false;
    }

    static boolean impersonatesBrand(String host, String registrable) {
        String compactHost = host.replace("-", "");
        for (Brand brand : BRANDS) {
            if (compactHost.contains(brand.name()) && !brand.domains().contains(registrable)) {
                return true;
            }
        }
        return false;
    }

    /** True when the visible text names a domain different from the real destination. */
    static boolean displayTextMismatch(String displayText, String actualRegistrable, String actualHost) {
        if (displayText == null || displayText.isBlank() || actualHost == null) {
            return false;
        }
        Matcher m = DOMAIN_IN_TEXT.matcher(displayText.trim());
        if (!m.find()) {
            return false;
        }
        String shownHost = m.group(1).toLowerCase(Locale.ROOT);
        boolean explicitWebAddress = m.group().toLowerCase(Locale.ROOT).matches("^(https?://|www\\.).*");
        String shownTld = shownHost.substring(shownHost.lastIndexOf('.') + 1);
        if (!explicitWebAddress && !COMMON_TLDS.contains(shownTld)) {
            return false; // e.g. "invoice.pdf" or "v2.final" are not domains
        }
        if (shownHost.startsWith("www.")) {
            shownHost = shownHost.substring(4);
        }
        String shownRegistrable = registrableDomain(shownHost);
        if (shownRegistrable == null) {
            return false;
        }
        if (actualRegistrable == null) {
            return true; // text shows a domain but the link targets an IP or unparseable host
        }
        return !shownRegistrable.equals(actualRegistrable);
    }

    private static long countChar(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }

    record Brand(String name, Set<String> domains) {
    }
}
