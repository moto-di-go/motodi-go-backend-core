-- Seed géocodage Yaoundé MVP. Bonamoussadi (Douala) est volontairement omis (hors zone).
CREATE TABLE IF NOT EXISTS urban_places (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  label varchar(180) NOT NULL,
  kind varchar(40) NOT NULL DEFAULT 'NEIGHBORHOOD',
  geom geometry(Point, 4326) NOT NULL,
  in_service_area boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_urban_places_kind CHECK (kind IN ('NEIGHBORHOOD', 'POI', 'LANDMARK')),
  CONSTRAINT uq_urban_places_label UNIQUE (label),
  CONSTRAINT chk_urban_places_geom_valid CHECK (ST_IsValid(geom))
);

CREATE INDEX IF NOT EXISTS idx_urban_places_geom ON urban_places USING gist(geom);
CREATE INDEX IF NOT EXISTS idx_urban_places_kind_sort ON urban_places(kind, sort_order, label);

INSERT INTO urban_places (label, kind, geom, in_service_area, sort_order)
SELECT label, kind, ST_SetSRID(ST_MakePoint(lon, lat), 4326), true, sort_order
FROM (VALUES
  ('Bastos', 'NEIGHBORHOOD', 3.8960, 11.5100, 10),
  ('Nlongkak', 'NEIGHBORHOOD', 3.8850, 11.5120, 20),
  ('Tsinga', 'NEIGHBORHOOD', 3.8900, 11.4980, 30),
  ('Elig-Edzoa', 'NEIGHBORHOOD', 3.8750, 11.5180, 40),
  ('Mokolo', 'NEIGHBORHOOD', 3.8760, 11.5000, 50),
  ('Mvog-Mbi', 'NEIGHBORHOOD', 3.8630, 11.5320, 60),
  ('Essos', 'NEIGHBORHOOD', 3.8700, 11.5380, 70),
  ('Ngousso', 'NEIGHBORHOOD', 3.8830, 11.5450, 80),
  ('Omnisport', 'NEIGHBORHOOD', 3.8870, 11.5380, 90),
  ('Condor', 'NEIGHBORHOOD', 3.8480, 11.5120, 100),
  ('Mvan', 'NEIGHBORHOOD', 3.8220, 11.5370, 110),
  ('Melen', 'NEIGHBORHOOD', 3.8630, 11.4880, 120),
  ('Nkomkana', 'NEIGHBORHOOD', 3.8820, 11.5050, 130),
  ('Etoudi', 'NEIGHBORHOOD', 3.9210, 11.5210, 140),
  ('Marché Mokolo', 'POI', 3.8780, 11.5030, 200),
  ('Poste centrale', 'POI', 3.8660, 11.5190, 210),
  ('Université (Yaoundé I)', 'POI', 3.8570, 11.5010, 220),
  ('Aéroport de Nsimalen', 'POI', 3.7220, 11.5530, 230)
) AS seed(label, kind, lat, lon, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM urban_places p WHERE p.label = seed.label);
