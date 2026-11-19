package com.aishield.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the stable ID used to scope history and feedback to the authenticated user. */
@Component
public class CurrentUser {

    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("no authenticated user");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt && jwt.getSubject() != null) {
            return "google:" + jwt.getSubject();
        }
        if (DevAuthenticationFilter.DEV_PRINCIPAL.equals(principal)) {
            return "dev:" + DevAuthenticationFilter.DEV_PRINCIPAL;
        }
        throw new IllegalStateException("unsupported authentication");
    }
}
