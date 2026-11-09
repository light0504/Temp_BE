ALTER TABLE trip ADD COLUMN in_transit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trip ADD CONSTRAINT chk_trip_in_transit
    CHECK (NOT in_transit OR (execution_status = 'IN_PROGRESS' AND current_stop_position >= 0));
