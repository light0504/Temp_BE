ALTER TABLE trip_report ADD COLUMN cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_crew_event_stop_time ON crew_event(trip_id,stop_position,action,created_at);
CREATE INDEX idx_wishlist_assignment ON trip_wishlist(route_id,travel_date,slot,status);
