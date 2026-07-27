CREATE TABLE kyc_document_decisions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  document_id uuid,
  document_type text NOT NULL,
  file_name text,
  mime_type varchar(120),
  size_bytes bigint,
  decision text NOT NULL,
  reason text,
  admin_profile_id uuid REFERENCES admin_profiles(profile_id) ON DELETE SET NULL,
  decided_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_kyc_document_decisions_type CHECK (document_type IN ('CNI', 'DRIVER_LICENSE', 'VEHICLE_REGISTRATION', 'INSURANCE', 'PROFILE_PHOTO', 'AGENCY_DOCUMENT')),
  CONSTRAINT chk_kyc_document_decisions_decision CHECK (decision IN ('VALIDATE', 'REJECT')),
  CONSTRAINT chk_kyc_document_decisions_reject_reason CHECK (decision <> 'REJECT' OR reason IS NOT NULL),
  CONSTRAINT chk_kyc_document_decisions_size CHECK (size_bytes IS NULL OR size_bytes > 0)
);

CREATE INDEX idx_kyc_document_decisions_profile ON kyc_document_decisions(profile_id, decided_at DESC);
CREATE INDEX idx_kyc_document_decisions_document ON kyc_document_decisions(document_id, decided_at DESC);
