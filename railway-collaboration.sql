CREATE TABLE collaboration_requests (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    message VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_collaboration_submission
        FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_collaboration_sender
        FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_collaboration_recipient
        FOREIGN KEY (recipient_id) REFERENCES users(id),
    CONSTRAINT chk_collaboration_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE UNIQUE INDEX ux_collaboration_request_once
    ON collaboration_requests (submission_id, sender_id, recipient_id);

CREATE INDEX ix_collaboration_recipient_status
    ON collaboration_requests (recipient_id, status, created_at DESC);

CREATE INDEX ix_collaboration_sender_created
    ON collaboration_requests (sender_id, created_at DESC);

CREATE TABLE collaborations (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    user_1_id BIGINT NOT NULL,
    user_2_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_collaborations_submission
        FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_collaborations_user_1
        FOREIGN KEY (user_1_id) REFERENCES users(id),
    CONSTRAINT fk_collaborations_user_2
        FOREIGN KEY (user_2_id) REFERENCES users(id),
    CONSTRAINT ux_collaborations_unique
        UNIQUE (user_1_id, user_2_id, submission_id)
);

CREATE INDEX ix_collaborations_submission
    ON collaborations (submission_id, created_at DESC);

CREATE INDEX ix_collaborations_user_1
    ON collaborations (user_1_id, created_at DESC);

CREATE INDEX ix_collaborations_user_2
    ON collaborations (user_2_id, created_at DESC);

CREATE TABLE submission_similarities (
    id BIGSERIAL PRIMARY KEY,
    submission_a_id BIGINT NOT NULL,
    submission_b_id BIGINT NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_submission_similarities_a
        FOREIGN KEY (submission_a_id) REFERENCES submissions(id),
    CONSTRAINT fk_submission_similarities_b
        FOREIGN KEY (submission_b_id) REFERENCES submissions(id),
    CONSTRAINT ux_submission_similarities_pair
        UNIQUE (submission_a_id, submission_b_id)
);

CREATE TABLE similarity_keywords (
    similarity_id BIGINT NOT NULL,
    keyword VARCHAR(255),
    CONSTRAINT fk_similarity_keywords_similarity
        FOREIGN KEY (similarity_id) REFERENCES submission_similarities(id)
);

CREATE INDEX ix_submission_similarities_score
    ON submission_similarities (similarity_score DESC);

CREATE INDEX ix_submission_similarities_a
    ON submission_similarities (submission_a_id);

CREATE INDEX ix_submission_similarities_b
    ON submission_similarities (submission_b_id);
