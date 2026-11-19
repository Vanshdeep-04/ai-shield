package com.aishield.analysis.risk;

import java.util.Locale;

/**
 * Risk bands. A score is a system-generated risk indicator, not a calibrated probability.
 * <ul>
 *   <li>LOW: 0-39</li>
 *   <li>MEDIUM: 40-69</li>
 *   <li>HIGH: 70-100</li>
 *   <li>UNKNOWN: the assessment could not be completed reliably (no score)</li>
 * </ul>
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH, UNKNOWN;

    public static final int MEDIUM_THRESHOLD = 40;
    public static final int HIGH_THRESHOLD = 70;

    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0-100");
        }
        if (score >= HIGH_THRESHOLD) {
            return HIGH;
        }
        return score >= MEDIUM_THRESHOLD ? MEDIUM : LOW;
    }

    /** Strict parsing: only the four documented values are accepted. */
    public static RiskLevel parseStrict(String value) {
        if (value == null) {
            throw new IllegalArgumentException("risk level is missing");
        }
        try {
            return RiskLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported risk level");
        }
    }
}
