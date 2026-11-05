-- V5__student_marketplace_posts.sql
-- Reposition Eexchange as a student marketplace: one required price on every
-- post, no listing types and no exchange/swap data.

-- 1. Remove exchange listings (images, reports and messages cascade).
DELETE FROM posts WHERE listing_type = 'EXCHANGE';

-- 2. Legacy free posts had no price; 0 now means free.
UPDATE posts SET price = 0 WHERE price IS NULL;

-- 3. Constraints that depend on the removed columns.
ALTER TABLE posts DROP CONSTRAINT chk_posts_price_consistency;
ALTER TABLE posts DROP CONSTRAINT chk_posts_listing_type;

-- 4. Remove the listing-type columns.
ALTER TABLE posts DROP COLUMN listing_type;
ALTER TABLE posts DROP COLUMN exchange_wanted;

-- 5. Every post now has a price; the inline price >= 0 check stays.
ALTER TABLE posts ALTER COLUMN price SET NOT NULL;

-- 6. Allow the future manual-review state without removing any existing value.
ALTER TABLE posts DROP CONSTRAINT chk_posts_status;
ALTER TABLE posts ADD CONSTRAINT chk_posts_status
    CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SOLD',
                      'HIDDEN', 'DELETED', 'MANUAL_REVIEW'));
