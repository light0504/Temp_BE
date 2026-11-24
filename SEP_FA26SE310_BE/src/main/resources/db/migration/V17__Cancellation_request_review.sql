ALTER TABLE trip_report ADD COLUMN cancellation_decision VARCHAR(12);
UPDATE trip_report SET cancellation_decision=
 CASE WHEN EXISTS (SELECT 1 FROM trip t WHERE t.id=trip_report.trip_id AND t.status='CANCELLED') THEN 'APPROVED'
      WHEN status='RESOLVED' THEN 'DECLINED' ELSE 'PENDING' END
 WHERE cancellation_requested=TRUE;
ALTER TABLE trip_report ADD CONSTRAINT chk_report_cancellation_decision
 CHECK (cancellation_decision IS NULL OR cancellation_decision IN ('PENDING','APPROVED','DECLINED'));
CREATE INDEX idx_report_cancellation_pending ON trip_report(cancellation_decision,created_at);
