package com.aishield.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns 401 (with the standard WWW-Authenticate header) and logs WHY a request was rejected,
 * e.g. missing token, expired token or wrong audience. The token itself is never logged.
 */
class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        String header = request.getHeader("Authorization");
        boolean hasBearer = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        String reason;
        if (!hasBearer) {
            reason = "no bearer token sent";
        } else if (ex instanceof OAuth2AuthenticationException oauth && oauth.getError() != null) {
            reason = oauth.getError().getErrorCode() + ": " + safe(oauth.getError().getDescription());
        } else {
            reason = safe(ex.getMessage());
        }
        log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), reason);
        delegate.commence(request, response, ex);
    }

    /** Keeps log lines short and free of line breaks. */
    private static String safe(String s) {
        if (s == null) {
            return "(no detail)";
        }
        String oneLine = s.replaceAll("[\\r\\n]+", " ");
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "…" : oneLine;
    }
}
