ALTER TABLE user_notification ADD COLUMN read_at TIMESTAMP;
CREATE INDEX idx_notification_unread ON user_notification(user_id,read_at);
