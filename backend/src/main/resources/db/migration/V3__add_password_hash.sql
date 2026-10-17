-- ============================================================
-- V3__add_password_hash.sql — Add password_hash to users
-- ============================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NOT NULL;
