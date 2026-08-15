-- Passagers sans CNI acceptée : repasser en PENDING pour exiger la validation KYC.
UPDATE profiles p
SET validation_status = 'PENDING',
    updated_at = NOW()
WHERE p.type = 'PASSENGER'
  AND p.validation_status = 'VALIDATED'
  AND NOT EXISTS (
    SELECT 1
    FROM official_documents d
    WHERE d.profile_id = p.id
      AND d.type = 'CNI'
      AND d.status = 'ACCEPTED'
  );
