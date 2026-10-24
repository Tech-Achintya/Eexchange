-- ============================================================
-- V4__schema_additions.sql — Categories Seed, Posts Enhancements,
--                            Contact Info, and Saved Posts (Wishlist)
-- ============================================================

-- 1. Seed Categories (Campus marketplace categories)
INSERT INTO categories (name, slug) VALUES
                                        ('Books & Notes', 'books-notes'),
                                        ('Mobiles & Electronics', 'mobiles-electronics'),
                                        ('Bicycles & Vehicles', 'bicycles-vehicles'),
                                        ('Hostel & Furniture', 'hostel-furniture'),
                                        ('Clothing & Fashion', 'clothing-fashion'),
                                        ('Sports & Fitness', 'sports-fitness'),
                                        ('Stationery & Supplies', 'stationery-supplies'),
                                        ('Others', 'others')
    ON CONFLICT (slug) DO NOTHING;

-- 2. Add hidden_reason to posts (USER_BLOCKED / REPORTS / ADMIN)
ALTER TABLE posts
    ADD COLUMN hidden_reason VARCHAR(30);

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_hidden_reason
        CHECK (hidden_reason IS NULL OR hidden_reason IN ('USER_BLOCKED', 'REPORTS', 'ADMIN'));

-- 3. Add moderation_attempts to posts (default 0)
ALTER TABLE posts
    ADD COLUMN moderation_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_moderation_attempts
        CHECK (moderation_attempts >= 0);

-- 4. Add nullable contact_info to interest_messages
ALTER TABLE interest_messages
    ADD COLUMN contact_info VARCHAR(255);

-- 5. Create saved_posts table (Wishlist / Bookmarks)
CREATE TABLE saved_posts
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    post_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_saved_posts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_saved_posts_post
        FOREIGN KEY (post_id) REFERENCES posts (id)
            ON DELETE CASCADE,

    -- A user can only save a specific post once
    CONSTRAINT uq_saved_posts_user_post UNIQUE (user_id, post_id)
);

-- Index for fast user wishlist retrieval
CREATE INDEX idx_saved_posts_user ON saved_posts (user_id);
CREATE INDEX idx_saved_posts_post ON saved_posts (post_id);
