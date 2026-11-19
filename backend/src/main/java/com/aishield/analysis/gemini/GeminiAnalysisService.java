package com.aishield.analysis.gemini;

import org.springframework.stereotype.Service;

import com.aishield.config.GeminiProperties;
import com.aishield.message.NormalizedMessage;

/**
 * AI-based content assessment using the Gemini Developer API, called directly from Java.
 * Replaces the original synopsis' Python / scikit-learn / FastAPI classifier pipeline.
 */
@Service
public class GeminiAnalysisService {

    private final GeminiClient client;
    private final GeminiResponseValidator validator;
    private final GeminiProperties props;

    public GeminiAnalysisService(GeminiClient client, GeminiResponseValidator validator, GeminiProperties props) {
        this.client = client;
        this.validator = validator;
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public String model() {
        return props.model();
    }

    /**
     * @throws GeminiException when the assessment cannot be obtained or fails validation
     */
    public Result analyze(NormalizedMessage message) {
        GeminiPrompts.Prompt prompt = GeminiPrompts.build(message, props.maxInputChars());
        String json = client.generateJson(prompt.systemInstruction(), prompt.input(), GeminiPrompts.responseSchema());
        return new Result(validator.validate(json, props.model()), prompt.truncated());
    }

    /**
     * @param assessment     validated AI assessment
     * @param inputTruncated true if the email body was shortened before it was sent to Gemini
     */
    public record Result(GeminiAssessment assessment, boolean inputTruncated) {
    }
}
