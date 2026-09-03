-- CORE-ANALYTICS-001: read indexes for KPI aggregation queries.

CREATE INDEX IF NOT EXISTS idx_sessions_issued_at ON sessions(issued_at);

CREATE INDEX IF NOT EXISTS idx_bookings_requested_at ON bookings(requested_at);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_status_confirmed_at
  ON payment_transactions(status, confirmed_at DESC);

CREATE INDEX IF NOT EXISTS idx_offline_operations_status_queued_at
  ON offline_operations(status, queued_at);
