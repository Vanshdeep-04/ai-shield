package com.aishield.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.aishield.analysis.url.UrlExtractor;
import com.aishield.api.dto.AnalysisRequest;

/**
 * Converts a client request into a {@link NormalizedMessage}.
 *
 * <p>HTML is parsed with jsoup purely as data: no scripts run, no images or remote resources
 * are loaded, and links are never followed. Invisible characters that are commonly used to
 * hide phishing keywords are removed.</p>
 */
@Component
public class MessageNormalizer {

    /** Upper bound on body text kept in memory for analysis. */
    static final int MAX_BODY_CHARS = 50_000;
    /** Upper bound on links analysed per message. */
    static final int MAX_LINKS = 100;

    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n\\s*\\n(\\s*\\n)+");
    /** Private-use character: survives jsoup's whitespace collapsing, then becomes a newline. */
    private static final String NEWLINE_MARKER = "";

    private final UrlExtractor urlExtractor;

    public MessageNormalizer(UrlExtractor urlExtractor) {
        this.urlExtractor = urlExtractor;
    }

    public NormalizedMessage normalize(AnalysisRequest request) {
        List<LinkRef> htmlLinks = new ArrayList<>();
        String htmlText = null;
        if (request.bodyHtml() != null && !request.bodyHtml().isBlank()) {
            Document doc = Jsoup.parse(request.bodyHtml());
            doc.select("script, style, head, noscript, template").remove();
            for (Element a : doc.select("a[href]")) {
                htmlLinks.add(new LinkRef(a.attr("href").trim(), cleanInline(a.text())));
            }
            htmlText = htmlToText(doc);
        }

        String text = request.bodyText() != null && !request.bodyText().isBlank() ? request.bodyText() : htmlText;
        String cleaned = cleanText(text);
        boolean truncated = cleaned.length() > MAX_BODY_CHARS;
        if (truncated) {
            cleaned = cleaned.substring(0, MAX_BODY_CHARS);
        }

        List<LinkRef> links = mergeLinks(request, htmlLinks, cleaned);

        String fromHeader = request.from() == null ? null : cleanInline(request.from());
        String senderAddress = EmailAddresses.extractAddress(fromHeader);
        String senderName = displayNameOf(fromHeader, senderAddress);
        String replyTo = EmailAddresses.extractAddress(request.replyTo());

        return new NormalizedMessage(
                request.source(),
                senderAddress,
                senderName,
                replyTo,
                cleanInline(request.subject()),
                cleaned,
                links,
                request.receivedAt(),
                truncated);
    }

    private List<LinkRef> mergeLinks(AnalysisRequest request, List<LinkRef> htmlLinks, String text) {
        Map<String, LinkRef> byUrl = new LinkedHashMap<>();
        // Links with visible text first, so the display-text/URL comparison is preserved.
        if (request.links() != null) {
            for (AnalysisRequest.LinkDto dto : request.links()) {
                addLink(byUrl, dto.url(), cleanInline(dto.text()));
            }
        }
        for (LinkRef link : htmlLinks) {
            addLink(byUrl, link.url(), link.displayText());
        }
        for (String url : urlExtractor.extract(text)) {
            addLink(byUrl, url, null);
        }
        List<LinkRef> result = new ArrayList<>(byUrl.values());
        return result.size() > MAX_LINKS ? result.subList(0, MAX_LINKS) : result;
    }

    private void addLink(Map<String, LinkRef> byUrl, String rawUrl, String displayText) {
        if (rawUrl == null) {
            return;
        }
        String url = INVISIBLE.matcher(rawUrl).replaceAll("").trim();
        String lower = url.toLowerCase(Locale.ROOT);
        boolean web = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.");
        if (!web || url.length() > 4096) {
            return; // mailto:, tel:, cid:, javascript:, fragments etc. are not web links
        }
        String text = displayText == null || displayText.isBlank() ? null : displayText;
        LinkRef existing = byUrl.get(url);
        if (existing == null || (existing.displayText() == null && text != null)) {
            byUrl.put(url, new LinkRef(url, text));
        }
    }

    private static String htmlToText(Document doc) {
        doc.select("br").before(NEWLINE_MARKER);
        doc.select("p, div, li, tr, table, h1, h2, h3, h4, h5, h6, blockquote").before(NEWLINE_MARKER);
        String flat = doc.body() == null ? doc.text() : doc.body().text();
        return flat.replace(NEWLINE_MARKER, "\n");
    }

    static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = INVISIBLE.matcher(s).replaceAll("");
        s = CONTROL.matcher(s).replaceAll("");
        s = SPACES.matcher(s).replaceAll(" ");
        s = s.replaceAll(" ?\\n ?", "\n");
        s = MANY_NEWLINES.matcher(s).replaceAll("\n\n");
        return s.trim();
    }

    static String cleanInline(String text) {
        if (text == null) {
            return null;
        }
        String s = INVISIBLE.matcher(text).replaceAll("");
        s = CONTROL.matcher(s).replaceAll(" ");
        s = s.replace('\n', ' ').replace('\t', ' ');
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    private static String displayNameOf(String fromHeader, String address) {
        if (fromHeader == null || fromHeader.isBlank()) {
            return null;
        }
        int lt = fromHeader.indexOf('<');
        String name = lt > 0 ? fromHeader.substring(0, lt) : (address == null ? fromHeader : "");
        name = name.replace("\"", "").trim();
        return name.isEmpty() ? null : name;
    }
}
