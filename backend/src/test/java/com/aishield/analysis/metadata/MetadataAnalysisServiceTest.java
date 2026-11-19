package com.aishield.analysis.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishield.message.MessageSource;
import com.aishield.message.NormalizedMessage;

class MetadataAnalysisServiceTest {

    private final MetadataAnalysisService service = new MetadataAnalysisService();

    private static NormalizedMessage msg(String address, String name, String replyTo) {
        return new NormalizedMessage(MessageSource.GMAIL, address, name, replyTo, "s", "body", List.of(), null, false);
    }

    @Test
    void consistentSenderHasNoIndicators() {
        MetadataAnalysisService.Result r = service.analyze(msg("service@paypal.com", "PayPal", "help@mail.paypal.com"));
        assertThat(r.indicators()).isEmpty();
        assertThat(r.score()).isZero();
        assertThat(r.senderAvailable()).isTrue();
    }

    @Test
    void detectsReplyToDomainMismatch() {
        MetadataAnalysisService.Result r = service.analyze(msg("billing@company.com", "Billing", "collect@gmail.com"));
        assertThat(r.indicators()).containsExactly(MetadataIndicator.REPLY_TO_DOMAIN_MISMATCH);
    }

    @Test
    void detectsBrandInDisplayNameFromOtherDomain() {
        MetadataAnalysisService.Result r = service.analyze(msg("alerts123@gmail.com", "HDFC Bank Alerts", null));
        assertThat(r.indicators()).contains(MetadataIndicator.DISPLAY_NAME_BRAND_MISMATCH);
    }

    @Test
    void brandMatchingIsWholeWordOnly() {
        assertThat(service.analyze(msg("shop@pineapple.example", "Pineapple Store", null)).indicators()).isEmpty();
    }

    @Test
    void detectsAddressInDisplayNameThatDiffersFromSender() {
        MetadataAnalysisService.Result r = service.analyze(msg("x@random.example", "security@paypal.com", null));
        assertThat(r.indicators()).contains(MetadataIndicator.DISPLAY_NAME_ADDRESS_MISMATCH);
    }

    @Test
    void missingSenderIsReportedNotScored() {
        MetadataAnalysisService.Result r = service.analyze(msg(null, null, null));
        assertThat(r.senderAvailable()).isFalse();
        assertThat(r.score()).isZero();
    }
}
