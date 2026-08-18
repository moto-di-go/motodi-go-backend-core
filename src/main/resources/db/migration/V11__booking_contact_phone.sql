-- MOB-INTER-001: contact phone on intercity booking subscription.
ALTER TABLE bookings
  ADD COLUMN contact_phone varchar(20);

ALTER TABLE bookings
  ADD CONSTRAINT chk_bookings_contact_phone
  CHECK (contact_phone IS NULL OR contact_phone ~ '^\+237[6-9][0-9]{8}$');
