-- AI Shield schema (MySQL 8). Stores analysis summaries and feedback only.
-- Never stored: full email bodies, OAuth tokens, Gemini API keys.
-- MySQL 8 defaults to the InnoDB engine and utf8mb4; the database itself is created with utf8mb4
-- (see backend/db/mysql-setup.sql).

CREATE TABLE analysis_record (
    id              CHAR(36)     NOT NULL,              -- UUID
    user_id         VARCHAR(255) NOT NULL,              -- "google:<sub>" from the verified Google ID token
    source          VARCHAR(16)  NOT NULL,              -- GMAIL | MANUAL
    sender_domain   VARCHAR(255),                       -- domain only, not the full address
    subject_preview VARCHAR(200),                       -- first 120 characters of the subject
    risk_level      VARCHAR(16)  NOT NULL,              -- LOW | MEDIUM | HIGH | UNKNOWN
    risk_score      INT,                                -- NULL when risk_level = UNKNOWN
    ai_status       VARCHAR(16)  NOT NULL,              -- COMPLETED | INCONCLUSIVE | UNAVAILABLE
    ai_model        VARCHAR(100),
    result_json     MEDIUMTEXT   NOT NULL,              -- explanation, indicators, link findings (no email body)
    created_at      DATETIME(6)  NOT NULL,              -- UTC
    PRIMARY KEY (id),
    CONSTRAINT chk_analysis_score CHECK (risk_score IS NULL OR (risk_score BETWEEN 0 AND 100))
);

CREATE INDEX idx_analysis_user_created ON analysis_record (user_id, created_at DESC);

CREATE TABLE analysis_feedback (
    id          CHAR(36)     NOT NULL,
    analysis_id CHAR(36)     NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    verdict     VARCHAR(32)  NOT NULL,                  -- ACCURATE | FALSE_POSITIVE | FALSE_NEGATIVE | UNSURE
    comment     VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_feedback_analysis UNIQUE (analysis_id),
    CONSTRAINT fk_feedback_analysis FOREIGN KEY (analysis_id) REFERENCES analysis_record (id) ON DELETE CASCADE
);
