CREATE TABLE IF NOT EXISTS chat_groups (
 group_id VARCHAR(128) PRIMARY KEY,
 translation_enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS webhook_events (
 event_id VARCHAR(128) PRIMARY KEY,
 group_id VARCHAR(128),
 reply_token VARCHAR(256),
 original_text TEXT,
 received_at BIGINT NOT NULL,
 event_time BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL,
 elapsed_ms BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_events_pending ON webhook_events(state,received_at);
CREATE TABLE IF NOT EXISTS chat_group_languages (
 group_id VARCHAR(128) PRIMARY KEY,
 partner_language VARCHAR(2) NOT NULL
);
CREATE TABLE IF NOT EXISTS chat_default_languages (
 conversation_id VARCHAR(128) PRIMARY KEY,
 default_language VARCHAR(2) NOT NULL
);
CREATE TABLE IF NOT EXISTS chat_quality_settings (
 conversation_id VARCHAR(128) PRIMARY KEY,
 verification_display VARCHAR(16) NOT NULL DEFAULT 'hidden',
 context_limit INT NULL
);
