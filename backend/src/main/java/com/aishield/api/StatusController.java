package com.aishield.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aishield.config.AppProperties;
import com.aishield.config.GeminiProperties;

/** Public, non-sensitive service status used by the app to show whether AI analysis is available. */
@RestController
public class StatusController {

    private final GeminiProperties gemini;
    private final AppProperties app;

    public StatusController(GeminiProperties gemini, AppProperties app) {
        this.gemini = gemini;
        this.app = app;
    }

    @GetMapping("/api/v1/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "ai-shield");
        body.put("apiVersion", "v1");
        body.put("aiConfigured", gemini.isConfigured());
        body.put("authMode", app.security().isDevMode() ? "dev" : "google");
        return body;
    }
}
