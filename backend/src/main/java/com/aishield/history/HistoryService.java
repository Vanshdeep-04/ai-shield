package com.aishield.history;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishield.api.dto.AnalysisResponse;
import com.aishield.api.dto.FeedbackDto;
import com.aishield.api.dto.FeedbackRequest;
import com.aishield.api.dto.HistoryItem;
import com.aishield.api.dto.PageResponse;
import com.aishield.error.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Analysis history and feedback. Every query is scoped to the authenticated user's ID,
 * so users can only see, change or delete their own records.
 */
@Service
public class HistoryService {

    static final int MAX_PAGE_SIZE = 50;

    private final AnalysisRecordRepository records;
    private final AnalysisFeedbackRepository feedback;
    private final ObjectMapper mapper;
    private final Clock clock;

    public HistoryService(AnalysisRecordRepository records, AnalysisFeedbackRepository feedback,
                          ObjectMapper mapper, Clock clock) {
        this.records = records;
        this.feedback = feedback;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void save(String userId, AnalysisResponse response, String aiModel) {
        String json;
        try {
            json = mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise analysis result", e);
        }
        records.save(new AnalysisRecord(response.id(), userId, response.source(),
                response.message().senderDomain(), response.message().subjectPreview(),
                response.riskLevel(), response.riskScore(), response.ai().status(), aiModel, json, response.createdAt()));
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> list(String userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<AnalysisRecord> result = records.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize));
        List<UUID> ids = result.getContent().stream().map(AnalysisRecord::getId).toList();
        Map<UUID, AnalysisFeedback> fbById = ids.isEmpty() ? Map.of()
                : feedback.findByAnalysisIdIn(ids).stream()
                        .collect(Collectors.toMap(AnalysisFeedback::getAnalysisId, Function.identity()));
        List<HistoryItem> items = result.getContent().stream().map(r -> new HistoryItem(
                r.getId(), r.getCreatedAt(), r.getSource(), r.getSenderDomain(), r.getSubjectPreview(),
                r.getRiskLevel(), r.getRiskScore(), r.getAiStatus(),
                fbById.containsKey(r.getId()) ? fbById.get(r.getId()).getVerdict() : null)).toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(String userId, UUID id) {
        AnalysisRecord record = records.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));
        AnalysisResponse stored;
        try {
            stored = mapper.readValue(record.getResultJson(), AnalysisResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored analysis could not be read", e);
        }
        FeedbackDto fb = feedback.findByAnalysisIdAndUserId(id, userId).map(FeedbackDto::from).orElse(null);
        return stored.withFeedback(fb);
    }

    @Transactional
    public void delete(String userId, UUID id) {
        AnalysisRecord record = records.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));
        feedback.deleteByAnalysisId(record.getId());
        records.delete(record);
    }

    @Transactional
    public int deleteAll(String userId) {
        feedback.deleteAllByUserId(userId);
        return records.deleteAllByUserId(userId);
    }

    @Transactional
    public FeedbackDto submitFeedback(String userId, UUID analysisId, FeedbackRequest request) {
        records.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        var now = clock.instant();
        AnalysisFeedback entity = feedback.findByAnalysisIdAndUserId(analysisId, userId)
                .map(existing -> {
                    existing.update(request.verdict(), comment, now);
                    return existing;
                })
                .orElseGet(() -> new AnalysisFeedback(UUID.randomUUID(), analysisId, userId, request.verdict(), comment, now));
        return FeedbackDto.from(feedback.save(entity));
    }
}
