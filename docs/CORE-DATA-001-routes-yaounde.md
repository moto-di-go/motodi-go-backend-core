# CORE-DATA-001 - Routes Yaounde GeoPackage

## Emplacement du fichier

Placer le fichier complet ici :

```text
motodi-go-backend-core/data/gis/routes_yaounde.gpkg
```

Le fichier n'est pas versionne et n'est pas embarque dans le JAR. En Docker Compose, le dossier est monte en lecture seule dans le conteneur `backend-core` :

```text
/app/data/gis/routes_yaounde.gpkg
```

## Convention PostGIS cible

Le GeoPackage sert d'entree d'import. La source de verite applicative reste PostgreSQL/PostGIS, dans la table `road_segments`.

Colonnes importantes :

- `source_name` : `routes_yaounde.gpkg`
- `source_layer` : nom de couche GeoPackage
- `source_feature_id` : identifiant stable de l'objet source si disponible
- `geom` : `geometry(MultiLineString, 4326)`
- `properties` : attributs source conserves en `jsonb`

Si la couche GeoPackage contient des `LineString`, l'import doit promouvoir les geometries en `MultiLineString`, par exemple avec l'option GDAL `-nlt PROMOTE_TO_MULTI`.

## Smoke test Yaounde

```sql
CREATE TABLE IF NOT EXISTS spatial_smoke_test (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  label text NOT NULL UNIQUE,
  geom geometry(Point, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_spatial_smoke_test_geom
  ON spatial_smoke_test USING gist (geom);

INSERT INTO spatial_smoke_test (label, geom)
VALUES ('Yaounde', ST_SetSRID(ST_MakePoint(11.52, 3.87), 4326))
ON CONFLICT (label) DO UPDATE SET geom = EXCLUDED.geom;

SELECT label, ST_AsText(geom) AS wkt, ST_SRID(geom) AS srid
FROM spatial_smoke_test
WHERE ST_DWithin(
  geom::geography,
  ST_SetSRID(ST_MakePoint(11.52, 3.87), 4326)::geography,
  1000
);
```
