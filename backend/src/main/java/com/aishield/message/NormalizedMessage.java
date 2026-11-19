package com.aishield.message;

import java.time.Instant;
import java.util.List;

/**
 * A message after source-specific extraction and normalisation. Every downstream component
 * (URL analysis, Gemini analysis, metadata checks, risk engine) works only with this type.
 *
 * @param source         where the message came from
 * @param senderAddress  sender email address (lower-cased), may be {@code null} for non-email sources
 * @param senderName     sender display name, may be {@code null}
 * @param replyTo        Reply-To address, may be {@code null}
 * @param subject        subject line, may be empty
 * @param bodyText       plain text body with HTML removed and whitespace normalised
 * @param links          links found in the body (deduplicated, in order of appearance)
 * @param receivedAt     when the message was received, may be {@code null}
 * @param bodyTruncated  true when the body exceeded the accepted length and was shortened
 */
public record NormalizedMessage(
        MessageSource source,
        String senderAddress,
        String senderName,
        String replyTo,
        String subject,
        String bodyText,
        List<LinkRef> links,
        Instant receivedAt,
        boolean bodyTruncated) {

    public NormalizedMessage {
        links = links == null ? List.of() : List.copyOf(links);
        subject = subject == null ? "" : subject;
        bodyText = bodyText == null ? "" : bodyText;
    }

    /** The domain part of the sender address, or {@code null}. */
    public String senderDomain() {
        return EmailAddresses.domainOf(senderAddress);
    }
}
