package com.aishield.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishield.analysis.AnalysisService;
import com.aishield.api.dto.AnalysisRequest;
import com.aishield.api.dto.AnalysisResponse;
import com.aishield.api.dto.FeedbackDto;
import com.aishield.api.dto.FeedbackRequest;
import com.aishield.api.dto.HistoryItem;
import com.aishield.api.dto.PageResponse;
import com.aishield.history.HistoryService;
import com.aishield.security.AnalysisRateLimiter;
import com.aishield.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * Analysis, history and feedback endpoints. All are scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/analyses")
@Validated
public class AnalysisController {

    private final AnalysisService analysisService;
    private final HistoryService historyService;
    private final AnalysisRateLimiter rateLimiter;
    private final CurrentUser currentUser;

    public AnalysisController(AnalysisService analysisService, HistoryService historyService,
                              AnalysisRateLimiter rateLimiter, CurrentUser currentUser) {
        this.analysisService = analysisService;
        this.historyService = historyService;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
    }

    /** Analyses one message and stores a summary in the user's history. */
    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        String userId = currentUser.id();
        rateLimiter.check(userId);
        return analysisService.analyze(userId, request);
    }

    /** Lists the user's previous analyses, newest first. */
    @GetMapping
    public PageResponse<HistoryItem> history(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return historyService.list(currentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable UUID id) {
        return historyService.get(currentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        historyService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Deletes the user's entire history (and its feedback). */
    @DeleteMapping
    public Map<String, Integer> deleteAll() {
        return Map.of("deleted", historyService.deleteAll(currentUser.id()));
    }

    /** Creates or replaces the user's feedback on one analysis. */
    @PutMapping("/{id}/feedback")
    public FeedbackDto feedback(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest request) {
        return historyService.submitFeedback(currentUser.id(), id, request);
    }
}
