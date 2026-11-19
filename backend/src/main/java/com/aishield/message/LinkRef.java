package com.aishield.message;

/**
 * A link found in a message.
 *
 * @param url         the actual target (href) of the link
 * @param displayText the visible link text, or {@code null} when the URL appeared as plain text
 */
public record LinkRef(String url, String displayText) {
}
