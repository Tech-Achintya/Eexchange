-- Lifetime edit counter for posts.
--
-- moderation_attempts is scoped to a single content revision and is reset on
-- every edit, so it cannot limit how many times a user can resubmit a post for
-- AI review. edit_count is never reset by user edits: once it reaches the
-- configured threshold the post is moved to MANUAL_REVIEW and stays there.
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_edit_count_non_negative CHECK (edit_count >= 0);
