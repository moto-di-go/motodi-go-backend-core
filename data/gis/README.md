# Local GIS datasets

Place the full Yaounde road network GeoPackage here:

```text
motodi-go-backend-core/data/gis/routes_yaounde.gpkg
```

This file is intentionally ignored by Git and Docker build context because it is operational data, not application source code.

Runtime paths:

- Native local backend: `./data/gis/routes_yaounde.gpkg`
- Docker Compose backend container: `/app/data/gis/routes_yaounde.gpkg`

The GeoPackage must be imported into PostGIS by a dedicated import script or job. Flyway migrations only create the target schema, including `road_segments`.
