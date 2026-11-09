CREATE TABLE booking_checkin_qr (
 booking_id BIGINT PRIMARY KEY REFERENCES booking(id),
 generation VARCHAR(36) NOT NULL,
 session_id VARCHAR(36) NOT NULL REFERENCES auth_session(id),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

