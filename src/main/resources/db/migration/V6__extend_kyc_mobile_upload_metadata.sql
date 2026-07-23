ALTER TABLE official_documents
  ADD COLUMN IF NOT EXISTS file_name varchar(255);

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS metadata_json jsonb;
