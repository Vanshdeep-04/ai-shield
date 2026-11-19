package com.aishield.error;

/** The user exceeded the per-minute analysis limit that protects the shared Gemini quota. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
