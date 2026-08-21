-- CORE-NOTIF-001: device tokens for push + EXPIRATION booking type

ALTER TABLE notifications
  DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
  ADD CONSTRAINT chk_notifications_type CHECK (type IN (
    'SUBSCRIPTION',
    'CONFIRMATION',
    'CANCELLATION',
    'DEPARTURE_REMINDER',
    'STATUS_CHANGE',
    'PAYMENT',
    'KYC_DECISION',
    'EXPIRATION'
  ));

CREATE TABLE notification_devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token text NOT NULL,
  platform text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_notification_devices_token UNIQUE (token),
  CONSTRAINT chk_notification_devices_platform CHECK (platform IN ('ANDROID', 'IOS'))
);

CREATE INDEX idx_notification_devices_user_id ON notification_devices(user_id);
