package com.aishield.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Production auth mode: Google ID token required for every /api call except status. */
@SpringBootTest(properties = {
        "aishield.security.auth-mode=google",
        "aishield.security.google-client-ids=test-client.apps.googleusercontent.com"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleAuthModeTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/analyses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analyses").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusIsPublicAndReportsGoogleMode() throws Exception {
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk()).andExpect(jsonPath("$.authMode").value("google"));
    }

    @Test
    void authenticatedUserSeesOnlyTheirOwnHistory() throws Exception {
        mvc.perform(get("/api/v1/analyses").with(jwt().jwt(j -> j.subject("user-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        int code = mvc.perform(get("/admin")).andReturn().getResponse().getStatus();
        assertThat(code).isIn(401, 403);
    }

    @Test
    void validatorsAcceptOnlyGoogleIssuerAndOurAudience() {
        var aud = SecurityConfig.audienceValidator(List.of("ours"));
        var iss = SecurityConfig.issuerValidator();
        assertThat(aud.validate(token("https://accounts.google.com", "ours")).hasErrors()).isFalse();
        assertThat(aud.validate(token("https://accounts.google.com", "someone-else")).hasErrors()).isTrue();
        assertThat(iss.validate(token("accounts.google.com", "ours")).hasErrors()).isFalse();
        assertThat(iss.validate(token("https://evil.example", "ours")).hasErrors()).isTrue();
    }

    private static Jwt token(String issuer, String audience) {
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("iss", issuer).audience(List.of(audience)).subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
