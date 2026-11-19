package com.aishield.analysis.metadata;

/** Deterministic sender/header indicators and their documented weights (points, capped at 100). */
public enum MetadataIndicator {

    DISPLAY_NAME_ADDRESS_MISMATCH(30, "The sender's display name contains an email address that differs from the real sender address"),
    DISPLAY_NAME_BRAND_MISMATCH(30, "The display name names a well-known brand, but the email was sent from a different domain"),
    REPLY_TO_DOMAIN_MISMATCH(20, "Replies would go to a different domain than the sender's");

    private final int weight;
    private final String description;

    MetadataIndicator(int weight, String description) {
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
