package com.aishield.analysis.gemini;

import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.aishield.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal REST client for the Gemini Developer API <em>Interactions</em> endpoint
 * ({@code POST {baseUrl}/interactions}), which Google's documentation recommends for new
 * projects. Requests are stateless ({@code "store": false}).
 *
 * <p>Reliability rules: connection and read timeouts are enforced; only transient failures
 * (rate limit, 5xx, timeout, network) are retried, at most {@code maxRetries} times with a fixed
 * backoff; quota exhaustion, auth and request errors are never retried. Neither the API key nor
 * the email content is ever logged.</p>
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private Sleeper sleeper = millis -> Thread.sleep(millis);

    @Autowired
    public GeminiClient(GeminiProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this(props, builder.requestFactory(requestFactory(props)).baseUrl(props.baseUrl()).build(), mapper);
    }

    /** Visible for tests (lets a mock server be bound to the RestClient). */
    GeminiClient(GeminiProperties props, RestClient restClient, ObjectMapper mapper) {
        this.props = props;
        this.restClient = restClient;
        this.mapper = mapper;
    }

    private static JdkClientHttpRequestFactory requestFactory(GeminiProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    /** For tests: replaces the backoff sleep. */
    void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * Sends one structured-output request and returns the model's JSON text.
     *
     * @param systemInstruction trusted analysis instructions
     * @param input             the untrusted email, already wrapped in delimiters
     * @param responseSchema    JSON schema the output must follow
     */
    public String generateJson(String systemInstruction, String input, Map<String, Object> responseSchema) {
        if (!props.isConfigured()) {
            throw new GeminiException(GeminiException.Reason.NOT_CONFIGURED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("system_instruction", systemInstruction);
        body.put("input", input);
        body.put("store", false);
        body.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json",
                "schema", responseSchema));
        body.put("generation_config", Map.of("max_output_tokens", props.maxOutputTokens()));

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String raw = send(body);
                return extractText(raw);
            } catch (GeminiException e) {
                if (!e.reason().retryable() || attempt > props.maxRetries()) {
                    log.warn("Gemini request failed: reason={} attempt={}", e.reason(), attempt);
                    throw e;
                }
                log.info("Gemini transient failure: reason={} attempt={}, retrying once after backoff", e.reason(), attempt);
                backoff();
            }
        }
    }

    private String send(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri("/interactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("x-goog-api-key", props.apiKey());
                        if (props.apiRevision() != null && !props.apiRevision().isBlank()) {
                            h.set("Api-Revision", props.apiRevision());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new GeminiException(classify(e.getStatusCode(), e.getResponseBodyAsString(StandardCharsets.UTF_8)), null);
        } catch (ResourceAccessException e) {
            throw new GeminiException(isTimeout(e) ? GeminiException.Reason.TIMEOUT : GeminiException.Reason.NETWORK_ERROR, null);
        } catch (RestClientException e) {
            throw new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE, null);
        }
    }

    static GeminiException.Reason classify(HttpStatusCode status, String responseBody) {
        int code = status.value();
        String lower = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        if (code == 429) {
            boolean quota = lower.contains("quota_exceeded") || lower.contains("per day") || lower.contains("perday")
                    || lower.contains("daily") || lower.contains("payment_required");
            return quota ? GeminiException.Reason.QUOTA_EXHAUSTED : GeminiException.Reason.RATE_LIMITED;
        }
        return switch (code) {
            case 400, 422 -> GeminiException.Reason.BAD_REQUEST;
            case 401, 403 -> GeminiException.Reason.AUTH_FAILED;
            case 402 -> GeminiException.Reason.QUOTA_EXHAUSTED;
            case 404 -> GeminiException.Reason.MODEL_NOT_FOUND;
            case 408, 504 -> GeminiException.Reason.TIMEOUT;
            default -> code >= 500 ? GeminiException.Reason.UNAVAILABLE : GeminiException.Reason.BAD_REQUEST;
        };
    }

    /**
     * Pulls the model text out of an Interactions response:
     * {@code steps[type=model_output].content[type=text].text}.
     */
    String extractText(String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw == null ? "" : raw);
        } catch (Exception e) {
            throw new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE, null);
        }
        if (root == null || !root.isObject()) {
            throw new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE);
        }
        String status = root.path("status").asText("");
        if ("failed".equals(status) || "cancelled".equals(status)) {
            String errors = root.path("errors").toString().toLowerCase(Locale.ROOT);
            boolean blocked = errors.contains("safety") || errors.contains("blocked") || errors.contains("prohibited");
            throw new GeminiException(blocked ? GeminiException.Reason.BLOCKED : GeminiException.Reason.UNAVAILABLE);
        }
        if (!status.isEmpty() && !"completed".equals(status)) {
            // in_progress, incomplete, requires_action... are not usable for a single synchronous call
            throw new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode step : root.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                    text.append(content.path("text").asText());
                }
            }
        }
        if (text.isEmpty()) {
            throw new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE);
        }
        return text.toString();
    }

    private static boolean isTimeout(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof HttpTimeoutException || t instanceof InterruptedIOException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void backoff() {
        try {
            sleeper.sleep(props.retryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException(GeminiException.Reason.TIMEOUT);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
