CREATE TABLE booking_priority (
 email VARCHAR(254) PRIMARY KEY,
 priority_group VARCHAR(20) NOT NULL CHECK (priority_group IN ('STAFF','STUDENT')),
 updated_by BIGINT NOT NULL REFERENCES app_user(id), updated_at TIMESTAMP NOT NULL
);
INSERT INTO operation_lock(name) VALUES ('booking-priority');
ALTER TABLE trip ADD COLUMN staff_booking_opens_at TIMESTAMP;
ALTER TABLE trip ADD COLUMN priority_student_booking_opens_at TIMESTAMP;
ALTER TABLE trip ADD COLUMN regular_booking_opens_at TIMESTAMP;
ALTER TABLE trip ADD CONSTRAINT chk_trip_booking_opening CHECK (
 (staff_booking_opens_at IS NULL AND priority_student_booking_opens_at IS NULL AND regular_booking_opens_at IS NULL)
 OR (staff_booking_opens_at IS NOT NULL AND priority_student_booking_opens_at IS NOT NULL AND regular_booking_opens_at IS NOT NULL
 AND staff_booking_opens_at<=priority_student_booking_opens_at AND priority_student_booking_opens_at<=regular_booking_opens_at AND regular_booking_opens_at<departure_at));
ALTER TABLE trip_recurrence ADD COLUMN staff_booking_opens_at TIMESTAMP;
ALTER TABLE trip_recurrence ADD COLUMN priority_student_booking_opens_at TIMESTAMP;
ALTER TABLE trip_recurrence ADD COLUMN regular_booking_opens_at TIMESTAMP;

