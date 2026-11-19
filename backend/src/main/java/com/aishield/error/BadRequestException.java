package com.aishield.error;

/** A semantically invalid request (beyond bean validation). The message is safe to show to users. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
