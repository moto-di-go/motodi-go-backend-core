-- Link / create a primary vehicle for transporters that already uploaded VEHICLE_PHOTO without vehicleId.

UPDATE transporter_profiles tp
SET vehicle_id = (
  SELECT v.id
  FROM vehicles v
  WHERE v.owner_profile_id = tp.profile_id
    AND v.active = true
  ORDER BY CASE v.validation_status
    WHEN 'VALIDATED' THEN 0
    WHEN 'PENDING' THEN 1
    ELSE 2
  END,
  v.created_at ASC
  LIMIT 1
)
WHERE tp.vehicle_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM vehicles v
    WHERE v.owner_profile_id = tp.profile_id
      AND v.active = true
  );

INSERT INTO vehicles (owner_profile_id, type, capacity, validation_status, active)
SELECT tp.profile_id,
       'CAR',
       4,
       CASE
         WHEN EXISTS (
           SELECT 1
           FROM official_documents od
           WHERE od.profile_id = tp.profile_id
             AND od.type = 'VEHICLE_PHOTO'
             AND od.status = 'ACCEPTED'
         ) THEN 'VALIDATED'
         ELSE 'PENDING'
       END,
       true
FROM transporter_profiles tp
WHERE tp.vehicle_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM official_documents od
    WHERE od.profile_id = tp.profile_id
      AND od.type = 'VEHICLE_PHOTO'
  );

UPDATE transporter_profiles tp
SET vehicle_id = (
  SELECT v.id
  FROM vehicles v
  WHERE v.owner_profile_id = tp.profile_id
    AND v.active = true
  ORDER BY v.created_at DESC
  LIMIT 1
)
WHERE tp.vehicle_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM vehicles v
    WHERE v.owner_profile_id = tp.profile_id
      AND v.active = true
  );

UPDATE official_documents od
SET vehicle_id = tp.vehicle_id,
    updated_at = now()
FROM transporter_profiles tp
WHERE od.profile_id = tp.profile_id
  AND od.type = 'VEHICLE_PHOTO'
  AND od.vehicle_id IS NULL
  AND tp.vehicle_id IS NOT NULL;
