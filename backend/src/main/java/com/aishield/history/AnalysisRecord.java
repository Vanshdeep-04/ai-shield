package com.aishield.history;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aishield.analysis.risk.RiskAssessment;
import com.aishield.analysis.risk.RiskLevel;
import com.aishield.message.MessageSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A stored analysis summary. Contains no email body and no credentials. */
@Entity
@Table(name = "analysis_record")
public class AnalysisRecord {

    /** Stored as CHAR(36) text so it is readable in MySQL tools. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageSource source;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "subject_preview", length = 200)
    private String subjectPreview;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 16)
    private RiskAssessment.AiStatus aiStatus;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "result_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisRecord() {
    }

    public AnalysisRecord(UUID id, String userId, MessageSource source, String senderDomain, String subjectPreview,
                          RiskLevel riskLevel, Integer riskScore, RiskAssessment.AiStatus aiStatus, String aiModel,
                          String resultJson, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.source = source;
        this.senderDomain = senderDomain;
        this.subjectPreview = subjectPreview;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.aiStatus = aiStatus;
        this.aiModel = aiModel;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public MessageSource getSource() {
        return source;
    }

    public String getSenderDomain() {
        return senderDomain;
    }

    public String getSubjectPreview() {
        return subjectPreview;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public RiskAssessment.AiStatus getAiStatus() {
        return aiStatus;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
