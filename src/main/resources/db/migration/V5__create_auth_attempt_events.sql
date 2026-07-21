CREATE TABLE auth_attempt_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  phone_number varchar(20),
  event_type text NOT NULL,
  success boolean NOT NULL,
  reason_code varchar(80),
  ip_address inet,
  user_agent text,
  correlation_id varchar(120),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_auth_attempt_events_type CHECK (event_type IN (
    'REGISTER',
    'OTP_SEND',
    'OTP_VERIFY',
    'LOGIN',
    'REFRESH',
    'LOGOUT',
    'PASSWORD_FORGOT',
    'PASSWORD_RESET'
  ))
);

CREATE INDEX idx_auth_attempt_events_phone_created_at ON auth_attempt_events(phone_number, created_at DESC);
CREATE INDEX idx_auth_attempt_events_user_created_at ON auth_attempt_events(user_id, created_at DESC);
CREATE INDEX idx_auth_attempt_events_type_success_created_at ON auth_attempt_events(event_type, success, created_at DESC);
CREATE INDEX idx_auth_attempt_events_ip_created_at ON auth_attempt_events(ip_address, created_at DESC);
