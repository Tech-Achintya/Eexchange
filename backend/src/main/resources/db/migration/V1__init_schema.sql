-- ============================================================
-- V1__init_schema.sql — Eexchange Initial Schema
-- ============================================================

-- ========================
-- 1. colleges
-- ========================
CREATE TABLE colleges
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    city       VARCHAR(100),
    state      VARCHAR(100),
    is_others  BOOLEAN      NOT NULL DEFAULT false,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_colleges_name UNIQUE (name)
);

-- Only one "Others" row can ever exist
CREATE UNIQUE INDEX uq_colleges_one_others
    ON colleges (is_others) WHERE is_others = true;


-- ========================
-- 2. college_email_domains
-- ========================
CREATE TABLE college_email_domains
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    college_id BIGINT       NOT NULL,
    domain     VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ced_college
        FOREIGN KEY (college_id) REFERENCES colleges (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_ced_domain UNIQUE (domain),

                                       CONSTRAINT chk_ced_domain_lowercase
                                           CHECK (domain = lower(domain))
    );


-- ========================
-- 3. users
-- ========================
CREATE TABLE users
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    college_id          BIGINT       NOT NULL,
    is_college_verified BOOLEAN      NOT NULL DEFAULT false,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    block_reason        TEXT,
    blocked_at          TIMESTAMPTZ,
    blocked_until       TIMESTAMPTZ,
    email_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'TEMP_BLOCKED', 'PERM_BLOCKED')),

    CONSTRAINT chk_users_email_normalized
        CHECK (
            email = lower(trim(email))
                AND length(trim(email)) > 0
            ),
    CONSTRAINT chk_users_college_verification
        CHECK (
            is_college_verified = FALSE
                OR email_verified_at IS NOT NULL
            ),
            CONSTRAINT chk_users_blocked_until
            CHECK (
            (
            status = 'TEMP_BLOCKED'
            AND blocked_until IS NOT NULL
            )
            OR
            (
            status <> 'TEMP_BLOCKED'
            AND blocked_until IS NULL
            )
            ),
    CONSTRAINT fk_users_college
        FOREIGN KEY (college_id) REFERENCES colleges (id)
);


-- ========================
-- 4. categories
-- ========================
CREATE TABLE categories
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug),

    CONSTRAINT chk_categories_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
)
    );


-- ========================
-- 5. posts
-- ========================
CREATE TABLE posts
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    college_id       BIGINT       NOT NULL,
    category_id      BIGINT       NOT NULL,
    title            VARCHAR(150) NOT NULL,
    description      TEXT         NOT NULL,
    listing_type     VARCHAR(20)  NOT NULL,
    price            NUMERIC(10, 2) CHECK (price >= 0),
    exchange_wanted  VARCHAR(255),
    condition        VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    ai_review_result JSONB,
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ,

    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_posts_college
        FOREIGN KEY (college_id) REFERENCES colleges (id),

    CONSTRAINT fk_posts_category
        FOREIGN KEY (category_id) REFERENCES categories (id),

    CONSTRAINT chk_posts_listing_type
        CHECK (listing_type IN ('SELL', 'EXCHANGE', 'FREE')),

    CONSTRAINT chk_posts_condition
        CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),

    CONSTRAINT chk_posts_status
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SOLD', 'HIDDEN', 'DELETED')),


    CONSTRAINT chk_posts_price_consistency
        CHECK (
            (
                listing_type = 'SELL'
                    AND price IS NOT NULL
                    AND exchange_wanted IS NULL
                )
                OR
            (
                listing_type = 'FREE'
                    AND price IS NULL
                    AND exchange_wanted IS NULL
                )
                OR
            (
                listing_type = 'EXCHANGE'
                    AND price IS NULL
                    AND exchange_wanted IS NOT NULL
                    AND length(trim(exchange_wanted)) > 0
                )
            )

);


-- ========================
-- 6. post_images
-- ========================
CREATE TABLE post_images
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    position   SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_post_images_post
        FOREIGN KEY (post_id) REFERENCES posts (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_post_images_position
        CHECK (position >= 0),
    CONSTRAINT uq_post_images_position UNIQUE (post_id, position)
);


-- ========================
-- 7. admins
-- (must be before reports — reports references admins)
-- ========================
CREATE TABLE admins
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_admins_username UNIQUE (username),
    CONSTRAINT uq_admins_email UNIQUE (email),

    CONSTRAINT chk_admins_role
        CHECK (role IN ('SUPER_ADMIN', 'MODERATOR'))
);


-- ========================
-- 8. reports
-- ========================
CREATE TABLE reports
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id         BIGINT      NOT NULL,
    reporter_id     BIGINT,
    reason          VARCHAR(30) NOT NULL,
    comment         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by     BIGINT,
    resolved_at     TIMESTAMPTZ,
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_reports_post
        FOREIGN KEY (post_id) REFERENCES posts (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_reports_reporter
        FOREIGN KEY (reporter_id) REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_reports_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES admins (id)
            ON DELETE SET NULL,

    CONSTRAINT uq_reports_one_per_user UNIQUE (post_id, reporter_id),

    CONSTRAINT chk_reports_reason
        CHECK (reason IN ('SPAM', 'SCAM', 'INAPPROPRIATE', 'PROHIBITED_ITEM', 'OTHER')),

    CONSTRAINT chk_reports_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),

    CONSTRAINT chk_reports_resolution_consistency
        CHECK (
            (status = 'OPEN'
                AND resolved_by IS NULL
                AND resolved_at IS NULL)
                OR
            (status IN ('RESOLVED', 'DISMISSED')
                AND resolved_by IS NOT NULL
                AND resolved_at IS NOT NULL)
            )
);


-- ========================
-- 9. interest_messages
-- ========================
CREATE TABLE interest_messages
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT        NOT NULL,
    sender_id  BIGINT        NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    is_read    BOOLEAN       NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_im_post
        FOREIGN KEY (post_id) REFERENCES posts (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_interest_messages_nonblank
        CHECK (length(trim(message)) > 0),
    CONSTRAINT fk_im_sender
        FOREIGN KEY (sender_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_im_one_per_buyer UNIQUE (post_id, sender_id)
);


-- ========================
-- 10. admin_audit_log
-- ========================
CREATE TABLE admin_audit_log
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id    BIGINT      NOT NULL,
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id   BIGINT      NOT NULL,
    reason      TEXT,
    metadata    JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_id) REFERENCES admins (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_audit_action
        CHECK (action IN (
            'BLOCK_TEMP', 'BLOCK_PERM', 'UNBLOCK',
            'DELETE_POST', 'APPROVE_POST', 'REJECT_POST',
            'RESOLVE_REPORT', 'DISMISS_REPORT',
            'DEACTIVATE_COLLEGE', 'ACTIVATE_COLLEGE'
            )
) ,

    CONSTRAINT chk_audit_target_type
        CHECK (target_type IN ('USER', 'POST', 'REPORT', 'COLLEGE'))
);


-- ========================
-- INDEXES
-- ========================
CREATE INDEX idx_users_college_id ON users (college_id);
CREATE INDEX idx_users_status ON users (status);

-- Most important: powers the college dashboard feed
CREATE INDEX idx_posts_college_status_created
    ON posts (college_id, status, created_at DESC);

CREATE INDEX idx_posts_user_id ON posts (user_id);
CREATE INDEX idx_posts_category_id ON posts (category_id);
CREATE INDEX idx_posts_status ON posts (status);

CREATE INDEX idx_reports_status_created ON reports (status, created_at);
CREATE INDEX idx_reports_post_id ON reports (post_id);

CREATE INDEX idx_im_post_id ON interest_messages (post_id);
CREATE INDEX idx_im_sender_id ON interest_messages (sender_id);

CREATE INDEX idx_audit_admin_id ON admin_audit_log (admin_id);
CREATE INDEX idx_audit_target ON admin_audit_log (target_type, target_id);
CREATE INDEX idx_audit_created_at ON admin_audit_log (created_at DESC);

CREATE INDEX idx_reports_reporter_id
    ON reports (reporter_id);

CREATE INDEX idx_interest_messages_sender_created
    ON interest_messages (sender_id, created_at DESC);

CREATE INDEX idx_admin_audit_target_created
    ON admin_audit_log (target_type, target_id, created_at DESC);


-- ========================
-- AUTO updated_at TRIGGER
-- ========================
CREATE
OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= now();
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_colleges_updated_at
    BEFORE UPDATE
    ON colleges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE
    ON posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
