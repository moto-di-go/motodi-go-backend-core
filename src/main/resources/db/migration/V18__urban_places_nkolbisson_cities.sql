-- Quartiers Yaoundé manquants (Nkolbisson, etc.) + villes Cameroun pour l'autocomplete interurbain.
ALTER TABLE urban_places DROP CONSTRAINT IF EXISTS chk_urban_places_kind;
ALTER TABLE urban_places ADD CONSTRAINT chk_urban_places_kind
  CHECK (kind IN ('NEIGHBORHOOD', 'POI', 'LANDMARK', 'CITY'));

INSERT INTO urban_places (label, kind, geom, in_service_area, sort_order)
SELECT label, kind, ST_SetSRID(ST_MakePoint(lon, lat), 4326), in_service_area, sort_order
FROM (VALUES
  ('Nkolbisson', 'NEIGHBORHOOD', 3.8750, 11.4580, true, 125),
  ('Biyem-Assi', 'NEIGHBORHOOD', 3.8370, 11.4850, true, 126),
  ('Mendong', 'NEIGHBORHOOD', 3.8250, 11.4850, true, 127),
  ('Ngoa-Ekellé', 'NEIGHBORHOOD', 3.8630, 11.5000, true, 128),
  ('Mvog-Ada', 'NEIGHBORHOOD', 3.8600, 11.5280, true, 129),
  ('Odza', 'NEIGHBORHOOD', 3.8000, 11.5180, true, 131),
  ('Ekounou', 'NEIGHBORHOOD', 3.8450, 11.5350, true, 132),
  ('Nkolndongo', 'NEIGHBORHOOD', 3.8550, 11.5300, true, 133),
  ('Mimboman', 'NEIGHBORHOOD', 3.8600, 11.5500, true, 134),
  ('Ahala', 'NEIGHBORHOOD', 3.8050, 11.5000, true, 135),
  ('Etoa-Meki', 'NEIGHBORHOOD', 3.8900, 11.5250, true, 136),
  ('Simbock', 'NEIGHBORHOOD', 3.7900, 11.4900, true, 137),
  ('Yaoundé', 'CITY', 3.8667, 11.5167, false, 10),
  ('Douala', 'CITY', 4.0511, 9.7679, false, 20),
  ('Bafoussam', 'CITY', 5.4737, 10.4179, false, 30),
  ('Bamenda', 'CITY', 5.9597, 10.1460, false, 40),
  ('Garoua', 'CITY', 9.3010, 13.3920, false, 50),
  ('Maroua', 'CITY', 10.5910, 14.3150, false, 60),
  ('Ngaoundéré', 'CITY', 7.3277, 13.5847, false, 70),
  ('Buea', 'CITY', 4.1527, 9.2410, false, 80),
  ('Kribi', 'CITY', 2.9373, 9.9077, false, 90),
  ('Ebolowa', 'CITY', 2.9000, 11.1500, false, 100),
  ('Bertoua', 'CITY', 4.5773, 13.6846, false, 110),
  ('Limbé', 'CITY', 4.0181, 9.2053, false, 120),
  ('Kumba', 'CITY', 4.6363, 9.4469, false, 130),
  ('Dschang', 'CITY', 5.4440, 10.0532, false, 140),
  ('Foumban', 'CITY', 5.7293, 10.8987, false, 150),
  ('Edéa', 'CITY', 3.8000, 10.1333, false, 160),
  ('Nkongsamba', 'CITY', 4.9547, 9.9404, false, 170)
) AS seed(label, kind, lat, lon, in_service_area, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM urban_places p WHERE p.label = seed.label);
