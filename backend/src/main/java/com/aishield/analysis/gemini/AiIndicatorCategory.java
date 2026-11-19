package com.aishield.analysis.gemini;

/** Categories of content-based phishing evidence that Gemini may report. */
public enum AiIndicatorCategory {
    CREDENTIAL_REQUEST,
    SENSITIVE_DATA_REQUEST,
    IMPERSONATION,
    URGENCY_OR_THREAT,
    FINANCIAL_LURE,
    SUSPICIOUS_LINK_CONTEXT,
    INCONSISTENCY,
    OTHER
}
