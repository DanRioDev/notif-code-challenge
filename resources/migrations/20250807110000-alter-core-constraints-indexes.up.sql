-- Align constraints with domain and add indexes

-- Backfill existing data to satisfy new constraints
UPDATE users SET name = COALESCE(NULLIF(btrim(name), ''), 'User ' || id);
--;;
UPDATE users SET email = COALESCE(NULLIF(btrim(email), ''), 'user' || id || '@example.local');
--;;
UPDATE users SET phone = COALESCE(NULLIF(btrim(phone), ''), '+100000' || lpad(id::text, 6, '0'));
--;;
UPDATE users SET subscribed_categories = COALESCE(subscribed_categories, '{}'::text[]);
--;;
UPDATE users SET preferred_channels = COALESCE(preferred_channels, '{}'::text[]);
--;;

UPDATE messages SET category = COALESCE(NULLIF(btrim(category), ''), 'uncategorized');
--;;
UPDATE messages SET content = COALESCE(NULLIF(btrim(content), ''), '(no content)');
--;;

DELETE FROM notification_logs WHERE message_id IS NULL;
--;;

-- USERS constraints: enforce non-blank for name/email/phone, keep arrays NOT NULL
ALTER TABLE users
  ALTER COLUMN email SET NOT NULL,
  ALTER COLUMN phone SET NOT NULL;
--;;
ALTER TABLE users
  ADD CONSTRAINT users_name_nonblank CHECK (btrim(name) <> ''),
  ADD CONSTRAINT users_email_nonblank CHECK (btrim(email) <> ''),
  ADD CONSTRAINT users_phone_nonblank CHECK (btrim(phone) <> '');
--;;
-- USERS indexes for array fields (GIN)
CREATE INDEX IF NOT EXISTS idx_users_subscribed_categories_gin ON users USING GIN (subscribed_categories);
--;;
CREATE INDEX IF NOT EXISTS idx_users_preferred_channels_gin ON users USING GIN (preferred_channels);
--;;

-- MESSAGES constraints and indexes
ALTER TABLE messages
  ADD CONSTRAINT messages_category_nonblank CHECK (btrim(category) <> ''),
  ADD CONSTRAINT messages_content_nonblank CHECK (btrim(content) <> '');
--;;
CREATE INDEX IF NOT EXISTS idx_messages_category ON messages (category);
--;;

-- NOTIFICATION_LOGS constraints and indexes
ALTER TABLE notification_logs
  ALTER COLUMN message_id SET NOT NULL;
--;;
ALTER TABLE notification_logs
  ADD CONSTRAINT notification_logs_channel_nonblank CHECK (btrim(channel) <> ''),
  ADD CONSTRAINT notification_logs_status_nonblank CHECK (btrim(status) <> '');
--;;
CREATE INDEX IF NOT EXISTS idx_notification_logs_message_id ON notification_logs (message_id);

