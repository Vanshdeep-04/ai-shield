package com.aishield.analysis.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.aishield.analysis.url.UrlAnalysisService;
import com.aishield.message.EmailAddresses;
import com.aishield.message.NormalizedMessage;

/**
 * Checks the sender metadata that the client supplied (From and Reply-To headers).
 *
 * <p>Limitation: SPF/DKIM/DMARC authentication results are not checked, so a perfectly
 * matching sender address is <em>not</em> proof that the email is genuine.</p>
 */
@Service
public class MetadataAnalysisService {

    public Result analyze(NormalizedMessage message) {
        List<MetadataIndicator> hits = new ArrayList<>();
        String senderDomain = message.senderDomain();

        if (senderDomain != null && message.replyTo() != null) {
            String replyDomain = EmailAddresses.domainOf(message.replyTo());
            if (replyDomain != null && !sameOrganisation(senderDomain, replyDomain)) {
                hits.add(MetadataIndicator.REPLY_TO_DOMAIN_MISMATCH);
            }
        }

        String name = message.senderName();
        if (name != null && senderDomain != null) {
            String addressInName = EmailAddresses.extractAddress(name);
            if (addressInName != null && !addressInName.equalsIgnoreCase(message.senderAddress())) {
                hits.add(MetadataIndicator.DISPLAY_NAME_ADDRESS_MISMATCH);
            }
            if (namesOtherBrand(name, senderDomain)) {
                hits.add(MetadataIndicator.DISPLAY_NAME_BRAND_MISMATCH);
            }
        }

        int score = Math.min(100, hits.stream().mapToInt(MetadataIndicator::weight).sum());
        return new Result(hits, score, senderDomain != null);
    }

    private static boolean sameOrganisation(String a, String b) {
        String ra = UrlAnalysisService.registrableDomainOf(a);
        String rb = UrlAnalysisService.registrableDomainOf(b);
        return ra != null && ra.equals(rb);
    }

    /**
     * True when a whole word (or two adjacent words, e.g. "HDFC Bank") of the display name is a
     * known brand and the sender domain is not one of that brand's domains. Whole-word matching
     * avoids false positives such as "Pineapple Store".
     */
    private static boolean namesOtherBrand(String displayName, String senderDomain) {
        String[] tokens = displayName.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        String registrable = UrlAnalysisService.registrableDomainOf(senderDomain);
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].isEmpty()) {
                continue;
            }
            if (UrlAnalysisService.isBrandOutsideItsDomains(tokens[i], registrable)) {
                return true;
            }
            if (i + 1 < tokens.length && UrlAnalysisService.isBrandOutsideItsDomains(tokens[i] + tokens[i + 1], registrable)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param indicators      matched indicators
     * @param score           0-100 metadata component score
     * @param senderAvailable whether a sender address was available to check
     */
    public record Result(List<MetadataIndicator> indicators, int score, boolean senderAvailable) {
        public Result {
            indicators = List.copyOf(indicators);
        }
    }
}
