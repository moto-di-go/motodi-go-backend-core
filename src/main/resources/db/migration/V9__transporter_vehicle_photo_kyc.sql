-- CORE: require vehicle photo for TRANSPORTER KYC + link primary vehicle on transporter profile.

ALTER TABLE transporter_profiles
  ADD COLUMN IF NOT EXISTS vehicle_id uuid REFERENCES vehicles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transporter_profiles_vehicle
  ON transporter_profiles(vehicle_id)
  WHERE vehicle_id IS NOT NULL;

ALTER TABLE official_documents
  DROP CONSTRAINT IF EXISTS chk_official_documents_type;

ALTER TABLE official_documents
  ADD CONSTRAINT chk_official_documents_type CHECK (type IN (
    'CNI',
    'DRIVER_LICENSE',
    'VEHICLE_REGISTRATION',
    'INSURANCE',
    'PROFILE_PHOTO',
    'AGENCY_DOCUMENT',
    'VEHICLE_PHOTO'
  ));

ALTER TABLE kyc_document_decisions
  DROP CONSTRAINT IF EXISTS chk_kyc_document_decisions_type;

ALTER TABLE kyc_document_decisions
  ADD CONSTRAINT chk_kyc_document_decisions_type CHECK (document_type IN (
    'CNI',
    'DRIVER_LICENSE',
    'VEHICLE_REGISTRATION',
    'INSURANCE',
    'PROFILE_PHOTO',
    'AGENCY_DOCUMENT',
    'VEHICLE_PHOTO'
  ));

-- Already-validated transporters must re-submit a vehicle photo before operating again.
UPDATE profiles
SET validation_status = 'PENDING',
    updated_at = now()
WHERE type = 'TRANSPORTER'
  AND validation_status = 'VALIDATED'
  AND NOT EXISTS (
    SELECT 1
    FROM official_documents d
    WHERE d.profile_id = profiles.id
      AND d.type = 'VEHICLE_PHOTO'
      AND d.status = 'ACCEPTED'
  );
