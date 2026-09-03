UPDATE agency_profiles
SET billing_plan = 'STANDARD'
WHERE billing_plan IS NULL
   OR billing_plan NOT IN ('STANDARD', 'PREMIUM');

ALTER TABLE agency_profiles
	ALTER COLUMN billing_plan SET DEFAULT 'STANDARD';

ALTER TABLE agency_profiles
	ALTER COLUMN billing_plan SET NOT NULL;

ALTER TABLE agency_profiles
	ADD CONSTRAINT chk_agency_billing_plan CHECK (billing_plan IN ('STANDARD', 'PREMIUM'));

ALTER TABLE agency_profiles
	ADD COLUMN display_priority integer NOT NULL DEFAULT 0;

UPDATE agency_profiles
SET display_priority = 1
WHERE billing_plan = 'PREMIUM'
  AND display_priority < 1;
