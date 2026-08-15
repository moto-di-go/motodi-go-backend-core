CREATE TABLE IF NOT EXISTS road_vertices (
  id bigserial PRIMARY KEY,
  geom geometry(Point, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_road_vertices_geom ON road_vertices USING gist(geom);

CREATE TABLE IF NOT EXISTS road_edges (
  id bigserial PRIMARY KEY,
  source_vertex_id bigint NOT NULL REFERENCES road_vertices(id) ON DELETE CASCADE,
  target_vertex_id bigint NOT NULL REFERENCES road_vertices(id) ON DELETE CASCADE,
  road_segment_id uuid REFERENCES road_segments(id) ON DELETE SET NULL,
  geom geometry(LineString, 4326) NOT NULL,
  length_m numeric(12,2) NOT NULL,
  max_speed_kmh integer,
  CONSTRAINT chk_road_edges_length CHECK (length_m > 0)
);

CREATE INDEX IF NOT EXISTS idx_road_edges_geom ON road_edges USING gist(geom);
CREATE INDEX IF NOT EXISTS idx_road_edges_source ON road_edges(source_vertex_id);
CREATE INDEX IF NOT EXISTS idx_road_edges_target ON road_edges(target_vertex_id);

INSERT INTO road_segments (source_name, source_layer, source_feature_id, name, road_class, max_speed_kmh, geom)
SELECT 'seed_yaounde', 'grid', feature_id, name, 'residential', 40,
       ST_SetSRID(ST_Multi(ST_GeomFromText(wkt)), 4326)
FROM (VALUES
  ('yde-h1', 'Avenue Kennedy', 'LINESTRING(11.500 3.860, 11.520 3.860)'),
  ('yde-h2', 'Rue Nachtigal', 'LINESTRING(11.520 3.860, 11.540 3.860)'),
  ('yde-h3', 'Boulevard du 20 Mai', 'LINESTRING(11.500 3.880, 11.520 3.880)'),
  ('yde-h4', 'Rue de Narvick', 'LINESTRING(11.520 3.880, 11.540 3.880)'),
  ('yde-v1', 'Montée Mbankolo', 'LINESTRING(11.500 3.860, 11.500 3.880)'),
  ('yde-v2', 'Avenue Ahmadou Ahidjo', 'LINESTRING(11.520 3.860, 11.520 3.880)'),
  ('yde-v3', 'Rue de l''Independance', 'LINESTRING(11.540 3.860, 11.540 3.880)')
) AS seed(feature_id, name, wkt)
WHERE NOT EXISTS (SELECT 1 FROM road_segments WHERE source_name = 'seed_yaounde');

INSERT INTO pricing_rules (
  zone_code, zone_geom, time_start, time_end, base_fare, price_per_km,
  night_coefficient, weather_coefficient, active, effective_from
)
SELECT
  'CM-CE-YDE',
  ST_SetSRID(ST_Multi(ST_MakeEnvelope(11.38, 3.72, 11.64, 4.05, 4326)), 4326),
  TIME '00:00',
  TIME '23:59:59',
  500,
  35,
  1.250,
  1.150,
  true,
  now()
WHERE NOT EXISTS (SELECT 1 FROM pricing_rules WHERE zone_code = 'CM-CE-YDE');
