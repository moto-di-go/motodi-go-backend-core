CREATE TABLE driver_positions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  driver_profile_id uuid NOT NULL REFERENCES driver_profiles(profile_id) ON DELETE CASCADE,
  geom geometry(Point, 4326) NOT NULL,
  speed_kmh numeric(6,2),
  accuracy_meters numeric(8,2),
  captured_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  consent_confirmed boolean NOT NULL DEFAULT false,
  CONSTRAINT chk_driver_positions_speed CHECK (speed_kmh IS NULL OR speed_kmh >= 0),
  CONSTRAINT chk_driver_positions_accuracy CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
  CONSTRAINT chk_driver_positions_capture_order CHECK (captured_at <= received_at)
);

CREATE INDEX idx_driver_positions_driver_captured_at ON driver_positions(driver_profile_id, captured_at DESC);
CREATE INDEX idx_driver_positions_geom ON driver_positions USING gist(geom);

CREATE TABLE routes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  origin_geom geometry(Point, 4326) NOT NULL,
  destination_geom geometry(Point, 4326) NOT NULL,
  polyline geometry(LineString, 4326) NOT NULL,
  distance_km numeric(10,3) NOT NULL,
  duration_minutes integer NOT NULL,
  status text NOT NULL DEFAULT 'PROPOSED',
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_routes_status CHECK (status IN ('PROPOSED', 'ACTIVE', 'BLOCKED', 'ARCHIVED')),
  CONSTRAINT chk_routes_distance CHECK (distance_km >= 0),
  CONSTRAINT chk_routes_duration CHECK (duration_minutes >= 0)
);

CREATE INDEX idx_routes_origin_geom ON routes USING gist(origin_geom);
CREATE INDEX idx_routes_destination_geom ON routes USING gist(destination_geom);
CREATE INDEX idx_routes_polyline ON routes USING gist(polyline);
CREATE INDEX idx_routes_status ON routes(status);

CREATE TABLE route_alternatives (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id uuid NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  reason text NOT NULL,
  polyline geometry(LineString, 4326) NOT NULL,
  extra_distance_km numeric(10,3) NOT NULL DEFAULT 0,
  extra_duration_minutes integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_route_alternatives_distance CHECK (extra_distance_km >= 0),
  CONSTRAINT chk_route_alternatives_duration CHECK (extra_duration_minutes >= 0)
);

CREATE INDEX idx_route_alternatives_route ON route_alternatives(route_id);
CREATE INDEX idx_route_alternatives_polyline ON route_alternatives USING gist(polyline);

CREATE TABLE traffic_layers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type text NOT NULL,
  label varchar(180) NOT NULL,
  geom geometry(Geometry, 4326) NOT NULL,
  severity text NOT NULL DEFAULT 'LOW',
  valid_from timestamptz NOT NULL DEFAULT now(),
  valid_to timestamptz,
  active boolean NOT NULL DEFAULT true,
  source varchar(120),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_traffic_layers_type CHECK (type IN ('CONGESTION', 'ROADWORK', 'DAMAGED_ROAD', 'ALTERNATIVE_ROUTE')),
  CONSTRAINT chk_traffic_layers_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT chk_traffic_layers_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
  CONSTRAINT chk_traffic_layers_geom_valid CHECK (ST_IsValid(geom))
);

CREATE INDEX idx_traffic_layers_geom ON traffic_layers USING gist(geom);
CREATE INDEX idx_traffic_layers_active_type ON traffic_layers(active, type, valid_from, valid_to);

CREATE TABLE road_segments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_name varchar(160) NOT NULL DEFAULT 'routes_yaounde.gpkg',
  source_layer varchar(160),
  source_feature_id varchar(120),
  name varchar(180),
  road_class varchar(80),
  surface varchar(80),
  one_way boolean,
  max_speed_kmh integer,
  geom geometry(MultiLineString, 4326) NOT NULL,
  properties jsonb NOT NULL DEFAULT '{}'::jsonb,
  imported_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_road_segments_speed CHECK (max_speed_kmh IS NULL OR max_speed_kmh > 0),
  CONSTRAINT chk_road_segments_geom_valid CHECK (ST_IsValid(geom))
);

CREATE INDEX idx_road_segments_geom ON road_segments USING gist(geom);
CREATE INDEX idx_road_segments_name ON road_segments(name);
CREATE INDEX idx_road_segments_road_class ON road_segments(road_class);
CREATE UNIQUE INDEX uq_road_segments_source_feature ON road_segments(source_name, source_layer, source_feature_id)
  WHERE source_layer IS NOT NULL AND source_feature_id IS NOT NULL;

CREATE TABLE pricing_rules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_code varchar(80) NOT NULL,
  zone_geom geometry(MultiPolygon, 4326),
  time_start time NOT NULL,
  time_end time NOT NULL,
  base_fare numeric(12,2) NOT NULL,
  price_per_km numeric(12,2) NOT NULL,
  night_coefficient numeric(6,3) NOT NULL DEFAULT 1,
  weather_coefficient numeric(6,3) NOT NULL DEFAULT 1,
  active boolean NOT NULL DEFAULT true,
  effective_from timestamptz NOT NULL DEFAULT now(),
  effective_to timestamptz,
  created_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_pricing_rules_amounts CHECK (base_fare >= 0 AND price_per_km >= 0),
  CONSTRAINT chk_pricing_rules_coefficients CHECK (night_coefficient > 0 AND weather_coefficient > 0),
  CONSTRAINT chk_pricing_rules_effective_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
  CONSTRAINT chk_pricing_rules_zone_geom_valid CHECK (zone_geom IS NULL OR ST_IsValid(zone_geom))
);

CREATE INDEX idx_pricing_rules_zone_time ON pricing_rules(zone_code, active, time_start, time_end);
CREATE INDEX idx_pricing_rules_zone_geom ON pricing_rules USING gist(zone_geom);

CREATE TABLE fare_estimates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id uuid NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  pricing_rule_id uuid REFERENCES pricing_rules(id) ON DELETE SET NULL,
  amount_fcfa numeric(12,2) NOT NULL,
  distance_km numeric(10,3) NOT NULL,
  duration_minutes integer NOT NULL,
  applied_rules_trace text NOT NULL,
  calculated_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  CONSTRAINT chk_fare_estimates_amount CHECK (amount_fcfa >= 0),
  CONSTRAINT chk_fare_estimates_distance CHECK (distance_km >= 0),
  CONSTRAINT chk_fare_estimates_duration CHECK (duration_minutes >= 0),
  CONSTRAINT chk_fare_estimates_expiry CHECK (expires_at IS NULL OR expires_at > calculated_at)
);

CREATE INDEX idx_fare_estimates_route ON fare_estimates(route_id);
CREATE INDEX idx_fare_estimates_calculated_at ON fare_estimates(calculated_at DESC);

CREATE TABLE trips (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type text NOT NULL,
  status text NOT NULL DEFAULT 'CONFIRMED',
  passenger_profile_id uuid REFERENCES passenger_profiles(profile_id) ON DELETE SET NULL,
  driver_profile_id uuid REFERENCES driver_profiles(profile_id) ON DELETE SET NULL,
  transporter_profile_id uuid REFERENCES transporter_profiles(profile_id) ON DELETE SET NULL,
  route_id uuid REFERENCES routes(id) ON DELETE SET NULL,
  fare_estimate_id uuid REFERENCES fare_estimates(id) ON DELETE SET NULL,
  origin_geom geometry(Point, 4326),
  destination_geom geometry(Point, 4326),
  planned_departure_at timestamptz,
  started_at timestamptz,
  completed_at timestamptz,
  final_price_fcfa numeric(12,2),
  cancellation_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_trips_type CHECK (type IN ('URBAN', 'INTERCITY')),
  CONSTRAINT chk_trips_status CHECK (status IN ('CONFIRMED', 'APPROACHING', 'ON_BOARD', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_trips_final_price CHECK (final_price_fcfa IS NULL OR final_price_fcfa >= 0),
  CONSTRAINT chk_trips_completion_order CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_trips_passenger_status ON trips(passenger_profile_id, status);
CREATE INDEX idx_trips_driver_status ON trips(driver_profile_id, status);
CREATE INDEX idx_trips_origin_geom ON trips USING gist(origin_geom);
CREATE INDEX idx_trips_destination_geom ON trips USING gist(destination_geom);

CREATE TABLE urban_trips (
  trip_id uuid PRIMARY KEY REFERENCES trips(id) ON DELETE CASCADE,
  driver_approach_distance_km numeric(10,3),
  CONSTRAINT chk_urban_trips_approach_distance CHECK (driver_approach_distance_km IS NULL OR driver_approach_distance_km >= 0)
);

CREATE TABLE intercity_trips (
  trip_id uuid PRIMARY KEY REFERENCES trips(id) ON DELETE CASCADE,
  departure_city varchar(120) NOT NULL,
  destination_city varchar(120) NOT NULL,
  departure_quarter varchar(120),
  available_seats integer NOT NULL DEFAULT 0,
  CONSTRAINT chk_intercity_trips_available_seats CHECK (available_seats >= 0)
);

CREATE TABLE intercity_announcements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  publisher_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
  vehicle_id uuid REFERENCES vehicles(id) ON DELETE SET NULL,
  route_id uuid REFERENCES routes(id) ON DELETE SET NULL,
  departure_city varchar(120) NOT NULL,
  destination_city varchar(120) NOT NULL,
  departure_quarter varchar(120),
  departure_point_geom geometry(Point, 4326),
  destination_point_geom geometry(Point, 4326),
  departure_date date NOT NULL,
  departure_time time NOT NULL,
  price_fcfa numeric(12,2) NOT NULL,
  total_seats integer NOT NULL,
  remaining_seats integer NOT NULL,
  instructions text,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_intercity_announcements_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'FULL', 'CLOSED', 'CANCELLED')),
  CONSTRAINT chk_intercity_announcements_price CHECK (price_fcfa >= 0),
  CONSTRAINT chk_intercity_announcements_seats CHECK (total_seats > 0 AND remaining_seats >= 0 AND remaining_seats <= total_seats)
);

CREATE INDEX idx_intercity_announcements_search ON intercity_announcements(departure_city, destination_city, departure_date, status);
CREATE INDEX idx_intercity_announcements_price ON intercity_announcements(price_fcfa);
CREATE INDEX idx_intercity_announcements_departure_point ON intercity_announcements USING gist(departure_point_geom);
CREATE INDEX idx_intercity_announcements_destination_point ON intercity_announcements USING gist(destination_point_geom);

CREATE TABLE bookings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  announcement_id uuid NOT NULL REFERENCES intercity_announcements(id) ON DELETE CASCADE,
  passenger_profile_id uuid NOT NULL REFERENCES passenger_profiles(profile_id) ON DELETE RESTRICT,
  trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'PENDING',
  seats_requested integer NOT NULL DEFAULT 1,
  requested_at timestamptz NOT NULL DEFAULT now(),
  confirmed_at timestamptz,
  cancelled_at timestamptz,
  expires_at timestamptz,
  cancellation_reason text,
  CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'PAID', 'PENDING_PAYMENT')),
  CONSTRAINT chk_bookings_seats_requested CHECK (seats_requested > 0),
  CONSTRAINT chk_bookings_expiry CHECK (expires_at IS NULL OR expires_at > requested_at)
);

CREATE INDEX idx_bookings_announcement_status ON bookings(announcement_id, status);
CREATE INDEX idx_bookings_passenger_status ON bookings(passenger_profile_id, status);
CREATE UNIQUE INDEX uq_bookings_active_passenger_announcement ON bookings(announcement_id, passenger_profile_id)
  WHERE status IN ('PENDING', 'CONFIRMED', 'PAID', 'PENDING_PAYMENT');

CREATE TABLE seat_locks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  announcement_id uuid NOT NULL REFERENCES intercity_announcements(id) ON DELETE CASCADE,
  booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  seats_locked integer NOT NULL,
  locked_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  consumed boolean NOT NULL DEFAULT false,
  CONSTRAINT uq_seat_locks_booking UNIQUE (booking_id),
  CONSTRAINT chk_seat_locks_seats CHECK (seats_locked > 0),
  CONSTRAINT chk_seat_locks_expiry CHECK (expires_at > locked_at)
);

CREATE INDEX idx_seat_locks_announcement_expiry ON seat_locks(announcement_id, expires_at, consumed);

CREATE TABLE trip_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  status text NOT NULL,
  label varchar(180) NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  source varchar(80) NOT NULL,
  CONSTRAINT chk_trip_events_status CHECK (status IN ('CONFIRMED', 'APPROACHING', 'ON_BOARD', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_trip_events_trip_occurred_at ON trip_events(trip_id, occurred_at DESC);
