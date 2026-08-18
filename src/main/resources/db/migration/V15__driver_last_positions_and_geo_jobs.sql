CREATE TABLE driver_last_positions (
  driver_profile_id uuid PRIMARY KEY REFERENCES driver_profiles(profile_id) ON DELETE CASCADE,
  position_id uuid NOT NULL REFERENCES driver_positions(id) ON DELETE CASCADE,
  geom geometry(Point, 4326) NOT NULL,
  speed_kmh numeric(6,2),
  accuracy_meters numeric(8,2),
  captured_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL,
  consent_confirmed boolean NOT NULL DEFAULT false,
  CONSTRAINT chk_driver_last_positions_speed CHECK (speed_kmh IS NULL OR speed_kmh >= 0),
  CONSTRAINT chk_driver_last_positions_accuracy CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
  CONSTRAINT chk_driver_last_positions_capture_order CHECK (captured_at <= received_at)
);

CREATE INDEX idx_driver_last_positions_geom ON driver_last_positions USING gist(geom);
CREATE INDEX idx_driver_last_positions_captured_at ON driver_last_positions(captured_at DESC);

INSERT INTO driver_last_positions (
  driver_profile_id, position_id, geom, speed_kmh, accuracy_meters, captured_at, received_at, consent_confirmed
)
SELECT DISTINCT ON (driver_profile_id)
  driver_profile_id, id, geom, speed_kmh, accuracy_meters, captured_at, received_at, consent_confirmed
FROM driver_positions
ORDER BY driver_profile_id, captured_at DESC;
