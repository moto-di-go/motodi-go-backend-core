CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number varchar(20) NOT NULL,
  password_hash text NOT NULL,
  full_name varchar(160),
  email varchar(255),
  locale varchar(16) NOT NULL DEFAULT 'fr-CM',
  cgu_accepted boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'PENDING_PHONE_VERIFICATION',
  phone_verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
  CONSTRAINT chk_users_status CHECK (status IN ('PENDING_PHONE_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  access_token_hash text NOT NULL,
  refresh_token_hash text NOT NULL,
  device_id varchar(160),
  ip_address inet,
  issued_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  status text NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uq_sessions_access_token_hash UNIQUE (access_token_hash),
  CONSTRAINT uq_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
  CONSTRAINT chk_sessions_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
  CONSTRAINT chk_sessions_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_sessions_user_status ON sessions(user_id, status);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);

CREATE TABLE otp_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  destination_phone varchar(20) NOT NULL,
  purpose text NOT NULL,
  code_hash text NOT NULL,
  attempts integer NOT NULL DEFAULT 0,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_otp_codes_purpose CHECK (purpose IN ('REGISTRATION', 'LOGIN', 'PASSWORD_RESET', 'PAYMENT_CONFIRMATION')),
  CONSTRAINT chk_otp_codes_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_otp_codes_lookup ON otp_codes(destination_phone, purpose, expires_at);
CREATE INDEX idx_otp_codes_user ON otp_codes(user_id);

CREATE TABLE profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type text NOT NULL,
  validation_status text NOT NULL DEFAULT 'DRAFT',
  display_name varchar(120) NOT NULL,
  avatar_url text,
  average_rating numeric(3,2) NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_profiles_user_type UNIQUE (user_id, type),
  CONSTRAINT chk_profiles_type CHECK (type IN ('PASSENGER', 'MOTOTAXI', 'TRANSPORTER', 'AGENCY', 'ADMIN')),
  CONSTRAINT chk_profiles_validation_status CHECK (validation_status IN ('DRAFT', 'PENDING', 'VALIDATED', 'REJECTED', 'SUSPENDED')),
  CONSTRAINT chk_profiles_average_rating CHECK (average_rating >= 0 AND average_rating <= 5)
);

CREATE UNIQUE INDEX uq_profiles_one_active_per_user ON profiles(user_id) WHERE active;
CREATE INDEX idx_profiles_user_status ON profiles(user_id, validation_status);
CREATE INDEX idx_profiles_type_status ON profiles(type, validation_status);

CREATE TABLE passenger_profiles (
  profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  preferred_language varchar(16) NOT NULL DEFAULT 'fr',
  emergency_contact varchar(20)
);

CREATE TABLE driver_profiles (
  profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  availability text NOT NULL DEFAULT 'OFFLINE',
  gps_consent boolean NOT NULL DEFAULT false,
  last_availability_change_at timestamptz,
  CONSTRAINT chk_driver_profiles_availability CHECK (availability IN ('AVAILABLE', 'OCCUPIED', 'OFFLINE'))
);

CREATE INDEX idx_driver_profiles_availability ON driver_profiles(availability);

CREATE TABLE moto_taxi_profiles (
  profile_id uuid PRIMARY KEY REFERENCES driver_profiles(profile_id) ON DELETE CASCADE,
  license_number varchar(80),
  operating_zone varchar(160)
);

CREATE UNIQUE INDEX uq_moto_taxi_profiles_license_number ON moto_taxi_profiles(license_number) WHERE license_number IS NOT NULL;

CREATE TABLE transporter_profiles (
  profile_id uuid PRIMARY KEY REFERENCES driver_profiles(profile_id) ON DELETE CASCADE,
  permit_number varchar(80),
  company_name varchar(160)
);

CREATE UNIQUE INDEX uq_transporter_profiles_permit_number ON transporter_profiles(permit_number) WHERE permit_number IS NOT NULL;

CREATE TABLE agency_profiles (
  profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  legal_name varchar(180) NOT NULL,
  trade_register_number varchar(80),
  billing_plan varchar(80)
);

CREATE UNIQUE INDEX uq_agency_profiles_trade_register_number ON agency_profiles(trade_register_number) WHERE trade_register_number IS NOT NULL;

CREATE TABLE admin_profiles (
  profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  admin_role text NOT NULL,
  permissions text[] NOT NULL DEFAULT '{}',
  CONSTRAINT chk_admin_profiles_role CHECK (admin_role IN ('PLATFORM_ADMIN', 'SUPPORT_AGENT', 'REGULATOR', 'AGENCY_MANAGER'))
);

CREATE TABLE vehicles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
  type text NOT NULL,
  plate_number varchar(32),
  brand varchar(80),
  model varchar(80),
  capacity integer NOT NULL,
  color varchar(40),
  validation_status text NOT NULL DEFAULT 'DRAFT',
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_vehicles_type CHECK (type IN ('MOTORCYCLE', 'CAR', 'VAN', 'BUS')),
  CONSTRAINT chk_vehicles_validation_status CHECK (validation_status IN ('DRAFT', 'PENDING', 'VALIDATED', 'REJECTED', 'SUSPENDED')),
  CONSTRAINT chk_vehicles_capacity CHECK (capacity > 0)
);

CREATE UNIQUE INDEX uq_vehicles_plate_number ON vehicles(plate_number) WHERE plate_number IS NOT NULL;
CREATE INDEX idx_vehicles_owner_status ON vehicles(owner_profile_id, validation_status);

CREATE TABLE official_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  vehicle_id uuid REFERENCES vehicles(id) ON DELETE SET NULL,
  type text NOT NULL,
  status text NOT NULL DEFAULT 'UPLOADED',
  encrypted_storage_key text NOT NULL,
  mime_type varchar(120) NOT NULL,
  size_bytes bigint NOT NULL,
  checksum varchar(160) NOT NULL,
  rejection_reason text,
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_official_documents_type CHECK (type IN ('CNI', 'DRIVER_LICENSE', 'VEHICLE_REGISTRATION', 'INSURANCE', 'PROFILE_PHOTO', 'AGENCY_DOCUMENT')),
  CONSTRAINT chk_official_documents_status CHECK (status IN ('UPLOADED', 'ENCRYPTED', 'PENDING_REVIEW', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
  CONSTRAINT chk_official_documents_size CHECK (size_bytes > 0)
);

CREATE UNIQUE INDEX uq_official_documents_checksum ON official_documents(checksum);
CREATE INDEX idx_official_documents_profile_type_status ON official_documents(profile_id, type, status);
CREATE INDEX idx_official_documents_vehicle ON official_documents(vehicle_id);

CREATE TABLE validation_decisions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  admin_profile_id uuid REFERENCES admin_profiles(profile_id) ON DELETE SET NULL,
  decision text NOT NULL,
  reason text,
  decided_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_validation_decisions_decision CHECK (decision IN ('VALIDATE', 'REJECT', 'SUSPEND')),
  CONSTRAINT chk_validation_decisions_reject_reason CHECK (decision <> 'REJECT' OR reason IS NOT NULL)
);

CREATE INDEX idx_validation_decisions_profile ON validation_decisions(profile_id, decided_at DESC);
CREATE INDEX idx_validation_decisions_admin ON validation_decisions(admin_profile_id);

CREATE TABLE document_access_audits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL REFERENCES official_documents(id) ON DELETE CASCADE,
  actor_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
  action text NOT NULL,
  actor_role varchar(80) NOT NULL,
  accessed_at timestamptz NOT NULL DEFAULT now(),
  correlation_id varchar(120),
  CONSTRAINT chk_document_access_audits_action CHECK (action IN ('VIEW', 'DOWNLOAD', 'APPROVE', 'REJECT'))
);

CREATE INDEX idx_document_access_audits_document ON document_access_audits(document_id, accessed_at DESC);
CREATE INDEX idx_document_access_audits_actor ON document_access_audits(actor_profile_id, accessed_at DESC);
