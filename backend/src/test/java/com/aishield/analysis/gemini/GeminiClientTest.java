package com.aishield.analysis.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.aishield.config.GeminiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiClientTest {

    private static final String BASE = "https://gemini.test/v1beta";
    private static final String URL = BASE + "/interactions";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    private MockRestServiceServer server;
    private final AtomicInteger sleeps = new AtomicInteger();

    private GeminiClient client(String apiKey, int maxRetries) {
        GeminiProperties props = new GeminiProperties(apiKey, "test-model", BASE, "2026-05-20",
                Duration.ofSeconds(1), Duration.ofSeconds(1), maxRetries, Duration.ofMillis(1), 15000, 1024);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient c = new GeminiClient(props, builder.build(), new ObjectMapper());
        c.setSleeper(ms -> sleeps.incrementAndGet());
        return c;
    }

    static String interaction(String status, String text) {
        return """
                {"id":"i1","object":"interaction","status":"%s","model":"test-model",
                 "steps":[{"type":"thought","signature":"abc"},
                          {"type":"model_output","content":[{"type":"text","text":%s}]}],
                 "usage":{"total_tokens":10}}
                """.formatted(status, new ObjectMapper().valueToTree(text).toString());
    }

    @Test
    void sendsStatelessStructuredRequestAndExtractsText() {
        GeminiClient c = client("secret-key", 1);
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(header("Api-Revision", "2026-05-20"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.system_instruction").value("SYS"))
                .andExpect(jsonPath("$.input").value("INPUT"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format.schema.type").value("object"))
                .andRespond(withSuccess(interaction("completed", "{\"ok\":true}"), MediaType.APPLICATION_JSON));

        assertThat(c.generateJson("SYS", "INPUT", SCHEMA)).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void missingApiKeyFailsWithoutAnyHttpCall() {
        GeminiClient c = client("  ", 1);
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.NOT_CONFIGURED);
        server.verify(); // no requests expected
    }

    @Test
    void retriesRateLimitOnceThenSucceeds() {
        GeminiClient c = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"slow down\"}}"));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(interaction("completed", "{}"), MediaType.APPLICATION_JSON));
        assertThat(c.generateJson("s", "i", SCHEMA)).isEqualTo("{}");
        assertThat(sleeps.get()).isEqualTo(1);
        server.verify();
    }

    @Test
    void quotaExhaustionIsNotRetried() {
        GeminiClient c = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"code\":\"quota_exceeded\",\"message\":\"Requests per day exceeded\"}}"));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.QUOTA_EXHAUSTED);
        assertThat(sleeps.get()).isZero();
        server.verify();
    }

    @Test
    void serverOverloadIsRetriedAtMostMaxRetriesTimes() {
        GeminiClient c = client("k", 1);
        server.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.UNAVAILABLE);
        server.verify();
    }

    @Test
    void timeoutIsReportedAsTimeout() {
        GeminiClient c = client("k", 0);
        server.expect(once(), requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.TIMEOUT);
    }

    @Test
    void networkErrorIsReportedAsNetworkError() {
        GeminiClient c = client("k", 0);
        server.expect(once(), requestTo(URL)).andRespond(withException(new UnknownHostException("no dns")));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.NETWORK_ERROR);
    }

    @Test
    void authAndRequestErrorsAreNotRetried() {
        GeminiClient c = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.AUTH_FAILED);
        server.verify();

        GeminiClient c2 = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertReason(() -> c2.generateJson("s", "i", SCHEMA), GeminiException.Reason.MODEL_NOT_FOUND);

        GeminiClient c3 = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertReason(() -> c3.generateJson("s", "i", SCHEMA), GeminiException.Reason.BAD_REQUEST);
    }

    @Test
    void malformedEnvelopesAreRejected() {
        String[] bodies = {
                "not json",
                "{}",
                "{\"status\":\"completed\",\"steps\":[]}",
                "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"image\"}]}]}",
                "{\"status\":\"incomplete\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}]}"
        };
        for (String body : bodies) {
            GeminiClient c = client("k", 1);
            server.expect(once(), requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.MALFORMED_RESPONSE);
        }
    }

    @Test
    void failedInteractionWithSafetyErrorIsBlocked() {
        GeminiClient c = client("k", 1);
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"failed\",\"errors\":[{\"code\":\"safety\",\"message\":\"blocked\"}]}", MediaType.APPLICATION_JSON));
        assertReason(() -> c.generateJson("s", "i", SCHEMA), GeminiException.Reason.BLOCKED);
    }

    @Test
    void exceptionMessagesNeverContainTheApiKey() {
        GeminiClient c = client("super-secret-key", 0);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"error\":{\"message\":\"key super-secret-key is invalid\"}}"));
        assertThatThrownBy(() -> c.generateJson("s", "i", SCHEMA))
                .isInstanceOf(GeminiException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("super-secret-key");
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void propertiesToStringHidesKey() {
        GeminiProperties props = new GeminiProperties("super-secret-key", "m", BASE, null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofMillis(1), 100, 100);
        assertThat(props.toString()).doesNotContain("super-secret-key");
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, GeminiException.Reason reason) {
        assertThatThrownBy(call).isInstanceOf(GeminiException.class)
                .extracting(e -> ((GeminiException) e).reason()).isEqualTo(reason);
    }
}
