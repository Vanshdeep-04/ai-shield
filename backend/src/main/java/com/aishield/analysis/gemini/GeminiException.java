package com.aishield.analysis.gemini;

/**
 * A failure while obtaining or validating a Gemini assessment. Messages are safe to show to
 * users: they never contain the API key, the email content or raw upstream responses.
 */
public class GeminiException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED("AI analysis is not configured on the server.", false),
        RATE_LIMITED("The AI service is receiving too many requests right now. Please try again in a minute.", true),
        QUOTA_EXHAUSTED("The AI service's free-tier quota is used up. Please try again later.", false),
        TIMEOUT("The AI service did not respond in time.", true),
        NETWORK_ERROR("The AI service could not be reached.", true),
        UNAVAILABLE("The AI service is temporarily unavailable.", true),
        AUTH_FAILED("The server's AI credentials were rejected.", false),
        MODEL_NOT_FOUND("The AI service is not available with the current server configuration.", false),
        BAD_REQUEST("The AI service rejected the request.", false),
        BLOCKED("The AI service declined to analyse this content.", false),
        MALFORMED_RESPONSE("The AI service returned a response that failed validation.", false);

        private final String userMessage;
        private final boolean retryable;

        Reason(String userMessage, boolean retryable) {
            this.userMessage = userMessage;
            this.retryable = retryable;
        }

        public String userMessage() {
            return userMessage;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Reason reason;

    public GeminiException(Reason reason) {
        this(reason, null);
    }

    public GeminiException(Reason reason, Throwable cause) {
        super(reason.userMessage(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
