# TourLK frontend

## Maps

Maps use [Leaflet](https://leafletjs.com/) with [OpenStreetMap](https://www.openstreetmap.org/) tiles and the
[Nominatim](https://nominatim.org/) search API. No API keys or billing are needed.

The public OpenStreetMap tile server and Nominatim are fine for a university project, but their
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/) and
[Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) rule out heavy production
traffic. The place search only runs on Enter / the Search button and at most once per second. For production,
use a hosted tile provider or your own tile/geocoding server and change `OSM_TILE_URL` in
`src/components/maps/mapShared.tsx`.
