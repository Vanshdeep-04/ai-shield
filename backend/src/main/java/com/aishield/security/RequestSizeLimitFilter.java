package com.aishield.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aishield.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects oversized request bodies before they are read (HTTP 413), and bodies of unknown
 * length (HTTP 411), so a client cannot stream an unbounded payload.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(AppProperties props) {
        this.maxBytes = props.maxRequestBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean hasBody = "POST".equals(request.getMethod()) || "PUT".equals(request.getMethod());
        if (hasBody) {
            long length = request.getContentLengthLong();
            if (length < 0 && request.getHeader("Transfer-Encoding") != null) {
                reject(response, HttpStatus.LENGTH_REQUIRED, "A Content-Length header is required.");
                return;
            }
            if (length > maxBytes) {
                reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\"}");
    }
}
