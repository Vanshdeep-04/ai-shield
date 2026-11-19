package com.aishield.analysis.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlExtractorTest {

    private final UrlExtractor extractor = new UrlExtractor();

    @Test
    void extractsHttpHttpsAndWwwUrls() {
        String text = "Visit https://example.com/a?b=1, or http://test.org. Also www.shop.example.net!";
        assertThat(extractor.extract(text))
                .containsExactly("https://example.com/a?b=1", "http://test.org", "www.shop.example.net");
    }

    @Test
    void trimsUnbalancedClosingParenthesisButKeepsBalancedOnes() {
        assertThat(extractor.extract("(see https://example.com/page)"))
                .containsExactly("https://example.com/page");
        assertThat(extractor.extract("https://en.wikipedia.org/wiki/Foo_(bar)"))
                .containsExactly("https://en.wikipedia.org/wiki/Foo_(bar)");
    }

    @Test
    void deduplicatesAndHandlesEmptyInput() {
        assertThat(extractor.extract("https://a.com https://a.com")).containsExactly("https://a.com");
        assertThat(extractor.extract("")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("no links here, just text.")).isEmpty();
    }

    @Test
    void ignoresNonWebSchemes() {
        assertThat(extractor.extract("mailto:a@b.com tel:+123 javascript:alert(1)")).isEmpty();
    }
}
