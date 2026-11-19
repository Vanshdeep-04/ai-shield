package com.aishield.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishield.analysis.url.UrlExtractor;
import com.aishield.api.dto.AnalysisRequest;

class MessageNormalizerTest {

    private final MessageNormalizer normalizer = new MessageNormalizer(new UrlExtractor());

    private static AnalysisRequest request(String from, String replyTo, String subject, String text, String html,
                                           List<AnalysisRequest.LinkDto> links) {
        return new AnalysisRequest(MessageSource.GMAIL, from, replyTo, subject, text, html, links, null);
    }

    @Test
    void parsesSenderHeaders() {
        NormalizedMessage m = normalizer.normalize(request("\"PayPal Service\" <Service@PayPal.com>",
                "Help <help@other.net>", "Hi", "body", null, null));
        assertThat(m.senderAddress()).isEqualTo("service@paypal.com");
        assertThat(m.senderName()).isEqualTo("PayPal Service");
        assertThat(m.senderDomain()).isEqualTo("paypal.com");
        assertThat(m.replyTo()).isEqualTo("help@other.net");
    }

    @Test
    void extractsTextAndAnchorsFromHtmlWithoutScripts() {
        String html = "<html><head><style>p{}</style></head><body><p>Dear user,</p><p>Please "
                + "<a href=\"https://evil.example/login\">www.mybank.com</a> now.</p>"
                + "<script>alert('x')</script><img src=\"https://tracker.example/pixel.gif\"></body></html>";
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "s", null, html, null));
        assertThat(m.bodyText()).contains("Dear user,").contains("Please www.mybank.com now.");
        assertThat(m.bodyText()).doesNotContain("alert").doesNotContain("p{}");
        assertThat(m.links()).anySatisfy(l -> {
            assertThat(l.url()).isEqualTo("https://evil.example/login");
            assertThat(l.displayText()).isEqualTo("www.mybank.com");
        });
        // the image source is not treated as a link
        assertThat(m.links()).noneMatch(l -> l.url().contains("tracker"));
    }

    @Test
    void prefersPlainTextBodyButKeepsHtmlAnchors() {
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "s", "Plain text version",
                "<p>HTML version <a href=\"https://x.example/\">here</a></p>", null));
        assertThat(m.bodyText()).isEqualTo("Plain text version");
        assertThat(m.links()).extracting(LinkRef::url).containsExactly("https://x.example/");
    }

    @Test
    void mergesClientLinksHtmlLinksAndPlainTextUrlsWithoutDuplicates() {
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "s",
                "see https://a.example/x and https://a.example/x again and mailto:x@y.com",
                null, List.of(new AnalysisRequest.LinkDto("https://a.example/x", "Open account"),
                        new AnalysisRequest.LinkDto("javascript:alert(1)", "bad"))));
        assertThat(m.links()).hasSize(1);
        assertThat(m.links().get(0).displayText()).isEqualTo("Open account");
    }

    @Test
    void removesInvisibleCharactersUsedToHideWords() {
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "Pa​ss​word reset",
                "Ver​ify your acc﻿ount", null, null));
        assertThat(m.subject()).isEqualTo("Password reset");
        assertThat(m.bodyText()).isEqualTo("Verify your account");
    }

    @Test
    void normalisesWhitespaceAndControlCharacters() {
        NormalizedMessage m = normalizer.normalize(request(null, null, null, "a\r\n\r\n\r\n\r\nb\u0007   c", null, null));
        assertThat(m.bodyText()).isEqualTo("a\n\nb c");
        assertThat(m.senderAddress()).isNull();
        assertThat(m.subject()).isEmpty();
    }

    @Test
    void truncatesVeryLongBodies() {
        String body = "x".repeat(MessageNormalizer.MAX_BODY_CHARS + 500);
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "s", body, null, null));
        assertThat(m.bodyText()).hasSize(MessageNormalizer.MAX_BODY_CHARS);
        assertThat(m.bodyTruncated()).isTrue();
    }

    @Test
    void handlesMalformedHtmlSafely() {
        NormalizedMessage m = normalizer.normalize(request("a@b.com", null, "s", null,
                "<div><p>Unclosed <a href='https://x.example'>link <b>bold</div></p><<<>>>", null));
        assertThat(m.bodyText()).contains("Unclosed");
        assertThat(m.links()).extracting(LinkRef::url).contains("https://x.example");
    }
}
