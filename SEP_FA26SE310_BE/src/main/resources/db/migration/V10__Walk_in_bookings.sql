ALTER TABLE booking ADD COLUMN walk_in_student_code VARCHAR(30);
ALTER TABLE booking ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE booking ADD CONSTRAINT chk_booking_passenger_identity CHECK (
    (user_id IS NOT NULL AND walk_in_student_code IS NULL)
    OR (user_id IS NULL AND walk_in_student_code IS NOT NULL)
);
