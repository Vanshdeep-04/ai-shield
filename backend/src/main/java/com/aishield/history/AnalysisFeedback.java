package com.aishield.history;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The user's opinion of one analysis result (at most one per analysis; re-submitting updates it). */
@Entity
@Table(name = "analysis_feedback")
public class AnalysisFeedback {

    public enum Verdict {
        /** The rating matched the user's judgement. */
        ACCURATE,
        /** Rated risky, but the email was legitimate. */
        FALSE_POSITIVE,
        /** Rated safe, but the email was phishing or a scam. */
        FALSE_NEGATIVE,
        UNSURE
    }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "analysis_id", nullable = false, unique = true, length = 36)
    private UUID analysisId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Verdict verdict;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnalysisFeedback() {
    }

    public AnalysisFeedback(UUID id, UUID analysisId, String userId, Verdict verdict, String comment, Instant now) {
        this.id = id;
        this.analysisId = analysisId;
        this.userId = userId;
        this.verdict = verdict;
        this.comment = comment;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Verdict verdict, String comment, Instant now) {
        this.verdict = verdict;
        this.comment = comment;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public String getUserId() {
        return userId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
