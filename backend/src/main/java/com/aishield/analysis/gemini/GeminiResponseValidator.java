package com.aishield.analysis.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.aishield.analysis.risk.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates Gemini's JSON output in Java. Structured output makes a valid shape likely, but the
 * model output is still untrusted: any missing field, wrong type, unsupported risk level or
 * out-of-range score causes the whole response to be rejected.
 */
@Component
public class GeminiResponseValidator {

    static final int MAX_EXPLANATION = 1500;
    static final int MAX_ACTION = 600;
    static final int MAX_INDICATORS = 8;
    static final int MAX_BENIGN = 4;
    static final int MAX_DESCRIPTION = 300;
    static final int MAX_EVIDENCE = 300;

    private final ObjectMapper mapper;

    public GeminiResponseValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public GeminiAssessment validate(String json, String model) {
        JsonNode root;
        try {
            root = mapper.readTree(stripCodeFence(json));
        } catch (Exception e) {
            throw invalid();
        }
        if (root == null || !root.isObject()) {
            throw invalid();
        }

        RiskLevel level;
        try {
            level = RiskLevel.parseStrict(requiredText(root, "riskLevel"));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }

        JsonNode scoreNode = root.get("riskScore");
        if (scoreNode == null || !scoreNode.isNumber() || !isWholeNumber(scoreNode)) {
            throw invalid();
        }
        int score = scoreNode.asInt();
        if (score < 0 || score > 100) {
            throw invalid();
        }

        JsonNode phishingNode = root.get("isPhishing");
        if (phishingNode == null || !phishingNode.isBoolean()) {
            throw invalid();
        }

        String explanation = truncate(requiredText(root, "explanation"), MAX_EXPLANATION);
        String action = truncate(requiredText(root, "recommendedAction"), MAX_ACTION);

        JsonNode indicatorsNode = root.get("indicators");
        if (indicatorsNode == null || !indicatorsNode.isArray()) {
            throw invalid();
        }
        List<GeminiAssessment.AiIndicator> indicators = new ArrayList<>();
        for (JsonNode item : indicatorsNode) {
            if (indicators.size() >= MAX_INDICATORS) {
                break;
            }
            if (!item.isObject()) {
                throw invalid();
            }
            String description = truncate(requiredText(item, "description"), MAX_DESCRIPTION);
            JsonNode evidenceNode = item.get("evidence");
            String evidence = evidenceNode != null && evidenceNode.isTextual() ? truncate(evidenceNode.asText().trim(), MAX_EVIDENCE) : "";
            indicators.add(new GeminiAssessment.AiIndicator(category(item.get("category")), description, evidence));
        }

        JsonNode benignNode = root.get("benignFactors");
        if (benignNode == null || !benignNode.isArray()) {
            throw invalid();
        }
        List<String> benign = new ArrayList<>();
        for (JsonNode item : benignNode) {
            if (item.isTextual() && !item.asText().isBlank() && benign.size() < MAX_BENIGN) {
                benign.add(truncate(item.asText().trim(), MAX_DESCRIPTION));
            }
        }

        return new GeminiAssessment(level, score, phishingNode.asBoolean(), explanation, indicators, benign, action, model);
    }

    /** Unknown categories are mapped to OTHER rather than failing the whole assessment. */
    private static AiIndicatorCategory category(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid();
        }
        try {
            return AiIndicatorCategory.valueOf(node.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AiIndicatorCategory.OTHER;
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid();
        }
        return value.asText().trim();
    }

    private static boolean isWholeNumber(JsonNode n) {
        return n.isIntegralNumber() || (n.isFloatingPointNumber() && n.asDouble() == Math.rint(n.asDouble()));
    }

    /** Tolerates a model wrapping JSON in a markdown code fence. */
    static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return t.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return t;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static GeminiException invalid() {
        return new GeminiException(GeminiException.Reason.MALFORMED_RESPONSE);
    }
}
