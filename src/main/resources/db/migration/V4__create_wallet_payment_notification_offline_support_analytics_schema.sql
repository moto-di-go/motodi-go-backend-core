CREATE TABLE wallets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  currency char(3) NOT NULL DEFAULT 'XAF',
  current_balance numeric(12,2) NOT NULL DEFAULT 0,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_wallets_user UNIQUE (user_id),
  CONSTRAINT chk_wallets_currency CHECK (currency = 'XAF'),
  CONSTRAINT chk_wallets_balance CHECK (current_balance >= 0),
  CONSTRAINT chk_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE TABLE payment_transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type text NOT NULL,
  status text NOT NULL DEFAULT 'INITIATED',
  amount numeric(12,2) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'XAF',
  external_reference varchar(160),
  idempotency_key varchar(180),
  provider text,
  payer_phone varchar(20),
  callback_signature text,
  booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
  passenger_wallet_id uuid REFERENCES wallets(id) ON DELETE SET NULL,
  receiver_wallet_id uuid REFERENCES wallets(id) ON DELETE SET NULL,
  platform_fee numeric(12,2),
  transporter_amount numeric(12,2),
  initiated_at timestamptz NOT NULL DEFAULT now(),
  confirmed_at timestamptz,
  failure_reason text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT chk_payment_transactions_type CHECK (type IN ('MOBILE_MONEY_DEPOSIT', 'RIDE_PAYMENT', 'NFC_PAYMENT', 'REFUND')),
  CONSTRAINT chk_payment_transactions_status CHECK (status IN ('INITIATED', 'PENDING_CALLBACK', 'CONFIRMED', 'FAILED', 'EXPIRED', 'REFUNDED')),
  CONSTRAINT chk_payment_transactions_currency CHECK (currency = 'XAF'),
  CONSTRAINT chk_payment_transactions_amount CHECK (amount >= 0),
  CONSTRAINT chk_payment_transactions_provider CHECK (provider IS NULL OR provider IN ('MTN_MOMO', 'ORANGE_MONEY', 'AGGREGATOR', 'WALLET', 'NFC')),
  CONSTRAINT chk_payment_transactions_fees CHECK (
    (platform_fee IS NULL OR platform_fee >= 0) AND
    (transporter_amount IS NULL OR transporter_amount >= 0)
  )
);

CREATE UNIQUE INDEX uq_payment_transactions_idempotency_key ON payment_transactions(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_transactions_provider_reference ON payment_transactions(provider, external_reference)
  WHERE provider IS NOT NULL AND external_reference IS NOT NULL;
CREATE INDEX idx_payment_transactions_booking ON payment_transactions(booking_id);
CREATE INDEX idx_payment_transactions_status_initiated_at ON payment_transactions(status, initiated_at DESC);

CREATE TABLE ledger_entries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id uuid NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
  payment_transaction_id uuid REFERENCES payment_transactions(id) ON DELETE SET NULL,
  type text NOT NULL,
  amount numeric(12,2) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'XAF',
  reference varchar(180),
  booked_at timestamptz NOT NULL DEFAULT now(),
  idempotency_key varchar(180),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT chk_ledger_entries_type CHECK (type IN ('CREDIT', 'DEBIT', 'REVERSAL', 'HOLD', 'RELEASE')),
  CONSTRAINT chk_ledger_entries_currency CHECK (currency = 'XAF'),
  CONSTRAINT chk_ledger_entries_amount CHECK (amount > 0)
);

CREATE UNIQUE INDEX uq_ledger_entries_idempotency_key ON ledger_entries(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_ledger_entries_wallet_booked_at ON ledger_entries(wallet_id, booked_at DESC);
CREATE INDEX idx_ledger_entries_payment_transaction ON ledger_entries(payment_transaction_id);

CREATE TABLE receipts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_transaction_id uuid NOT NULL REFERENCES payment_transactions(id) ON DELETE CASCADE,
  receipt_number varchar(80) NOT NULL,
  amount numeric(12,2) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'XAF',
  issued_at timestamptz NOT NULL DEFAULT now(),
  pdf_url text NOT NULL,
  CONSTRAINT uq_receipts_payment_transaction UNIQUE (payment_transaction_id),
  CONSTRAINT uq_receipts_receipt_number UNIQUE (receipt_number),
  CONSTRAINT chk_receipts_currency CHECK (currency = 'XAF'),
  CONSTRAINT chk_receipts_amount CHECK (amount >= 0)
);

CREATE TABLE nfc_payment_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id uuid NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
  payment_transaction_id uuid REFERENCES payment_transactions(id) ON DELETE SET NULL,
  signed_token text NOT NULL,
  amount numeric(12,2) NOT NULL,
  issued_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  status text NOT NULL DEFAULT 'ISSUED',
  CONSTRAINT uq_nfc_payment_tokens_signed_token UNIQUE (signed_token),
  CONSTRAINT chk_nfc_payment_tokens_amount CHECK (amount > 0),
  CONSTRAINT chk_nfc_payment_tokens_expiry CHECK (expires_at > issued_at),
  CONSTRAINT chk_nfc_payment_tokens_status CHECK (status IN ('ISSUED', 'EXCHANGED', 'PENDING_SYNC', 'CONSUMED', 'EXPIRED', 'REJECTED'))
);

CREATE INDEX idx_nfc_payment_tokens_wallet_status ON nfc_payment_tokens(wallet_id, status);

CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type text NOT NULL,
  title varchar(160) NOT NULL,
  body text NOT NULL,
  status text NOT NULL DEFAULT 'QUEUED',
  target_device_token text,
  scheduled_at timestamptz,
  sent_at timestamptz,
  read_at timestamptz,
  booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  payment_transaction_id uuid REFERENCES payment_transactions(id) ON DELETE SET NULL,
  trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_notifications_type CHECK (type IN ('SUBSCRIPTION', 'CONFIRMATION', 'CANCELLATION', 'DEPARTURE_REMINDER', 'STATUS_CHANGE', 'PAYMENT', 'KYC_DECISION')),
  CONSTRAINT chk_notifications_status CHECK (status IN ('QUEUED', 'SENT', 'READ', 'FAILED'))
);

CREATE INDEX idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_status_scheduled_at ON notifications(status, scheduled_at);

CREATE TABLE notification_preferences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  push_enabled boolean NOT NULL DEFAULT true,
  booking_updates boolean NOT NULL DEFAULT true,
  payment_alerts boolean NOT NULL DEFAULT true,
  marketing_enabled boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_notification_preferences_user UNIQUE (user_id)
);

CREATE TABLE sync_batches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  status text NOT NULL DEFAULT 'STARTED',
  CONSTRAINT chk_sync_batches_status CHECK (status IN ('STARTED', 'PARTIAL_SUCCESS', 'COMPLETED', 'FAILED')),
  CONSTRAINT chk_sync_batches_completion CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_sync_batches_user_started_at ON sync_batches(user_id, started_at DESC);

CREATE TABLE offline_map_areas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  area_name varchar(160) NOT NULL,
  bounding_box geometry(Polygon, 4326) NOT NULL,
  size_bytes bigint NOT NULL,
  downloaded_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  CONSTRAINT chk_offline_map_areas_size CHECK (size_bytes > 0),
  CONSTRAINT chk_offline_map_areas_expiry CHECK (expires_at > downloaded_at),
  CONSTRAINT chk_offline_map_areas_bbox_valid CHECK (ST_IsValid(bounding_box))
);

CREATE INDEX idx_offline_map_areas_user ON offline_map_areas(user_id);
CREATE INDEX idx_offline_map_areas_bbox ON offline_map_areas USING gist(bounding_box);

CREATE TABLE offline_operations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sync_batch_id uuid REFERENCES sync_batches(id) ON DELETE SET NULL,
  type text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key varchar(180) NOT NULL,
  status text NOT NULL DEFAULT 'QUEUED',
  retry_count integer NOT NULL DEFAULT 0,
  queued_at timestamptz NOT NULL DEFAULT now(),
  synced_at timestamptz,
  booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  nfc_token_id uuid REFERENCES nfc_payment_tokens(id) ON DELETE SET NULL,
  CONSTRAINT uq_offline_operations_user_idempotency UNIQUE (user_id, idempotency_key),
  CONSTRAINT chk_offline_operations_type CHECK (type IN ('BOOKING_CREATE', 'BOOKING_CANCEL', 'NFC_PAYMENT', 'PROFILE_UPDATE', 'SUPPORT_TICKET')),
  CONSTRAINT chk_offline_operations_status CHECK (status IN ('QUEUED', 'SYNCING', 'SYNCED', 'CONFLICT', 'FAILED')),
  CONSTRAINT chk_offline_operations_retry_count CHECK (retry_count >= 0),
  CONSTRAINT chk_offline_operations_synced_at CHECK (synced_at IS NULL OR synced_at >= queued_at)
);

CREATE INDEX idx_offline_operations_user_status ON offline_operations(user_id, status, queued_at);
CREATE INDEX idx_offline_operations_sync_batch ON offline_operations(sync_batch_id);

CREATE TABLE sync_conflicts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  offline_operation_id uuid NOT NULL REFERENCES offline_operations(id) ON DELETE CASCADE,
  code varchar(80) NOT NULL,
  message text NOT NULL,
  resolution text,
  detected_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz,
  CONSTRAINT uq_sync_conflicts_operation UNIQUE (offline_operation_id),
  CONSTRAINT chk_sync_conflicts_resolution CHECK (resolved_at IS NULL OR resolved_at >= detected_at)
);

CREATE TABLE ratings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  rater_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rated_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  stars integer NOT NULL,
  comment text,
  status text NOT NULL DEFAULT 'PUBLISHED',
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_ratings_trip_rater_rated UNIQUE (trip_id, rater_user_id, rated_profile_id),
  CONSTRAINT chk_ratings_stars CHECK (stars BETWEEN 1 AND 5),
  CONSTRAINT chk_ratings_status CHECK (status IN ('PUBLISHED', 'FLAGGED', 'HIDDEN'))
);

CREATE INDEX idx_ratings_rated_profile ON ratings(rated_profile_id, created_at DESC);

CREATE TABLE support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  assigned_admin_profile_id uuid REFERENCES admin_profiles(profile_id) ON DELETE SET NULL,
  category text NOT NULL,
  priority text NOT NULL DEFAULT 'MEDIUM',
  status text NOT NULL DEFAULT 'OPEN',
  subject varchar(180) NOT NULL,
  message text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  closed_at timestamptz,
  CONSTRAINT chk_support_tickets_category CHECK (category IN ('TRANSPORT', 'PAYMENT', 'ACCOUNT', 'SECURITY', 'EMERGENCY')),
  CONSTRAINT chk_support_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  CONSTRAINT chk_support_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_USER', 'RESOLVED', 'CLOSED')),
  CONSTRAINT chk_support_tickets_closed_at CHECK (closed_at IS NULL OR closed_at >= created_at)
);

CREATE INDEX idx_support_tickets_user_created_at ON support_tickets(user_id, created_at DESC);
CREATE INDEX idx_support_tickets_status_priority ON support_tickets(status, priority);

CREATE TABLE moderation_actions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_profile_id uuid REFERENCES admin_profiles(profile_id) ON DELETE SET NULL,
  target_type text NOT NULL,
  target_id uuid NOT NULL,
  action text NOT NULL,
  reason text,
  acted_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_moderation_actions_target_type CHECK (target_type IN ('RATING', 'SUPPORT_TICKET', 'USER', 'PROFILE', 'ANNOUNCEMENT')),
  CONSTRAINT chk_moderation_actions_action CHECK (action IN ('HIDE', 'RESTORE', 'ESCALATE', 'CLOSE', 'SUSPEND'))
);

CREATE INDEX idx_moderation_actions_target ON moderation_actions(target_type, target_id);
CREATE INDEX idx_moderation_actions_admin ON moderation_actions(admin_profile_id, acted_at DESC);

CREATE TABLE ai_recommendation_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  departure_city varchar(120) NOT NULL,
  destination_city varchar(120) NOT NULL,
  quarter varchar(120),
  preferred_start_time time,
  preferred_end_time time,
  requested_seats integer NOT NULL DEFAULT 1,
  anonymized_user_context text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_ai_recommendation_requests_requested_seats CHECK (requested_seats > 0)
);

CREATE INDEX idx_ai_recommendation_requests_route ON ai_recommendation_requests(departure_city, destination_city, created_at DESC);

CREATE TABLE ai_recommendations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES ai_recommendation_requests(id) ON DELETE CASCADE,
  announcement_id uuid REFERENCES intercity_announcements(id) ON DELETE SET NULL,
  score numeric(5,4) NOT NULL,
  reason text NOT NULL,
  generated_at timestamptz NOT NULL DEFAULT now(),
  clicked boolean NOT NULL DEFAULT false,
  CONSTRAINT chk_ai_recommendations_score CHECK (score >= 0 AND score <= 1)
);

CREATE INDEX idx_ai_recommendations_request_score ON ai_recommendations(request_id, score DESC);
CREATE INDEX idx_ai_recommendations_announcement ON ai_recommendations(announcement_id);

CREATE TABLE ai_usage_metrics (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  period date NOT NULL,
  call_count integer NOT NULL DEFAULT 0,
  accepted_suggestion_count integer NOT NULL DEFAULT 0,
  average_latency_ms numeric(12,2) NOT NULL DEFAULT 0,
  error_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_usage_metrics_period UNIQUE (period),
  CONSTRAINT chk_ai_usage_metrics_counts CHECK (
    call_count >= 0 AND
    accepted_suggestion_count >= 0 AND
    average_latency_ms >= 0 AND
    error_count >= 0
  )
);

CREATE TABLE kpi_snapshots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  period_start date NOT NULL,
  period_end date NOT NULL,
  monthly_active_users integer NOT NULL DEFAULT 0,
  active_verified_drivers integer NOT NULL DEFAULT 0,
  reservation_count integer NOT NULL DEFAULT 0,
  wallet_volume_fcfa numeric(14,2) NOT NULL DEFAULT 0,
  sla_availability numeric(5,2),
  avg_sig_sync_seconds numeric(12,2),
  ai_usage_rate numeric(6,4),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_kpi_snapshots_period UNIQUE (period_start, period_end),
  CONSTRAINT chk_kpi_snapshots_period CHECK (period_end >= period_start),
  CONSTRAINT chk_kpi_snapshots_counts CHECK (
    monthly_active_users >= 0 AND
    active_verified_drivers >= 0 AND
    reservation_count >= 0 AND
    wallet_volume_fcfa >= 0
  )
);
