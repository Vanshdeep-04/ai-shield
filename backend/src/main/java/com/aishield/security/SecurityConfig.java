package com.aishield.security;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.aishield.config.AppProperties;

/**
 * API security.
 *
 * <p><b>google mode (default)</b>: every {@code /api/**} request (except the public status
 * endpoint) must carry {@code Authorization: Bearer <Google ID token>}. The token's signature is
 * checked against Google's public keys, and its issuer, expiry and audience (our OAuth Web client ID)
 * are validated. The user's stable Google account ID ({@code sub}) scopes their history.
 * The Gmail access token is never sent to this server.</p>
 *
 * <p><b>dev mode</b>: for local testing with Postman/curl only; all requests act as one demo user.</p>
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(corsSource(props)))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoggingAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/status").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());

        if (props.security().isDevMode()) {
            log.warn("AISHIELD_AUTH_MODE=dev: authentication is DISABLED and all requests act as one demo user. "
                    + "Use only for local testing.");
            http.addFilterBefore(new DevAuthenticationFilter(), BearerTokenAuthenticationFilter.class);
        } else {
            List<String> clientIds = props.security().googleClientIds().stream().filter(s -> s != null && !s.isBlank()).toList();
            if (clientIds.isEmpty()) {
                throw new IllegalStateException("GOOGLE_OAUTH_WEB_CLIENT_ID must be set (or set AISHIELD_AUTH_MODE=dev for local testing).");
            }
            http.oauth2ResourceServer(o -> o
                    .jwt(j -> j.decoder(googleIdTokenDecoder(clientIds)))
                    .authenticationEntryPoint(new LoggingAuthenticationEntryPoint()));
            log.info("Google ID token authentication enabled for {} client ID(s).", clientIds.size());
        }
        return http.build();
    }

    static JwtDecoder googleIdTokenDecoder(List<String> clientIds) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), issuerValidator(), audienceValidator(clientIds)));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> issuerValidator() {
        return jwt -> {
            String iss = jwt.getClaimAsString("iss");
            return iss != null && GOOGLE_ISSUERS.contains(iss)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Untrusted token issuer", null));
        };
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(List<String> clientIds) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            boolean ok = aud != null && aud.stream().anyMatch(clientIds::contains);
            return ok ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token was not issued for AI Shield", null));
        };
    }

    private static CorsConfigurationSource corsSource(AppProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins().stream().filter(s -> !s.isBlank()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
