-- Phase 6 / Vague 0: NFC holds, offline quotas, ratings tags, support context, notif types

-- Wallet available-balance holds for NFC issuance
ALTER TABLE wallets
  ADD COLUMN IF NOT EXISTS held_balance numeric(12,2) NOT NULL DEFAULT 0;

ALTER TABLE wallets
  DROP CONSTRAINT IF EXISTS chk_wallets_held_balance;

ALTER TABLE wallets
  ADD CONSTRAINT chk_wallets_held_balance CHECK (
    held_balance >= 0 AND held_balance <= current_balance
  );

-- NFC token enrichment
ALTER TABLE nfc_payment_tokens
  ADD COLUMN IF NOT EXISTS jti uuid,
  ADD COLUMN IF NOT EXISTS booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS payer_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS receiver_wallet_id uuid REFERENCES wallets(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS payload_json jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE nfc_payment_tokens
SET jti = id
WHERE jti IS NULL;

ALTER TABLE nfc_payment_tokens
  ALTER COLUMN jti SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfc_payment_tokens_jti ON nfc_payment_tokens(jti);
CREATE INDEX IF NOT EXISTS idx_nfc_payment_tokens_booking ON nfc_payment_tokens(booking_id);
CREATE INDEX IF NOT EXISTS idx_nfc_payment_tokens_trip ON nfc_payment_tokens(trip_id);
CREATE INDEX IF NOT EXISTS idx_nfc_payment_tokens_payer ON nfc_payment_tokens(payer_user_id);

-- Pre-authorized offline NFC envelope (remaining quota refreshed when online)
CREATE TABLE IF NOT EXISTS nfc_offline_quotas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  wallet_id uuid NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
  remaining_amount numeric(12,2) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'XAF',
  expires_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_nfc_offline_quotas_user UNIQUE (user_id),
  CONSTRAINT chk_nfc_offline_quotas_amount CHECK (remaining_amount >= 0),
  CONSTRAINT chk_nfc_offline_quotas_currency CHECK (currency = 'XAF')
);

CREATE INDEX IF NOT EXISTS idx_nfc_offline_quotas_expires ON nfc_offline_quotas(expires_at);

-- Rating quick tags (ponctuel, conduite sûre, …)
ALTER TABLE ratings
  ADD COLUMN IF NOT EXISTS tags text[] NOT NULL DEFAULT '{}';

-- Support ticket number + contextual FKs
ALTER TABLE support_tickets
  ADD COLUMN IF NOT EXISTS ticket_number varchar(40),
  ADD COLUMN IF NOT EXISTS trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS payment_transaction_id uuid REFERENCES payment_transactions(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS offline_operation_id uuid REFERENCES offline_operations(id) ON DELETE SET NULL;

UPDATE support_tickets
SET ticket_number = 'MDG-SUP-' || upper(substr(replace(id::text, '-', ''), 1, 10))
WHERE ticket_number IS NULL;

ALTER TABLE support_tickets
  ALTER COLUMN ticket_number SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_support_tickets_ticket_number ON support_tickets(ticket_number);
CREATE INDEX IF NOT EXISTS idx_support_tickets_trip ON support_tickets(trip_id);
CREATE INDEX IF NOT EXISTS idx_support_tickets_booking ON support_tickets(booking_id);

CREATE TABLE IF NOT EXISTS support_ticket_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  support_ticket_id uuid NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
  author_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  author_admin_profile_id uuid REFERENCES admin_profiles(profile_id) ON DELETE SET NULL,
  body text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_support_ticket_messages_body CHECK (char_length(trim(body)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_messages_ticket
  ON support_ticket_messages(support_ticket_id, created_at);

CREATE TABLE IF NOT EXISTS support_ticket_attachments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  support_ticket_id uuid NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
  object_key text NOT NULL,
  content_type varchar(120) NOT NULL,
  size_bytes bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_support_ticket_attachments_size CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_attachments_ticket
  ON support_ticket_attachments(support_ticket_id);

-- Notification types for post-trip / support
ALTER TABLE notifications
  DROP CONSTRAINT IF EXISTS chk_notifications_type;

ALTER TABLE notifications
  ADD CONSTRAINT chk_notifications_type CHECK (type IN (
    'SUBSCRIPTION',
    'CONFIRMATION',
    'CANCELLATION',
    'DEPARTURE_REMINDER',
    'STATUS_CHANGE',
    'PAYMENT',
    'KYC_DECISION',
    'EXPIRATION',
    'TRIP_COMPLETED',
    'RATING',
    'SUPPORT'
  ));

-- Stable sync conflict codes reference (documentation via check on new rows optional)
COMMENT ON TABLE sync_conflicts IS
  'Stable codes: TOKEN_EXPIRED, TOKEN_ALREADY_CONSUMED, INVALID_SIGNATURE, AMOUNT_MISMATCH, TRIP_MISMATCH, SEAT_EXPIRED, BOOKING_CANCELLED';
