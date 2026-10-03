import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'
import {
  googleDirectionsLink,
  googleMapsLink,
  MapResizer,
  OSM_ATTRIBUTION,
  OSM_TILE_URL,
  pinIcon,
} from './mapShared'

export interface LocationMapProps {
  latitude?: number | null
  longitude?: number | null
  name?: string
}

/** Scroll-wheel zoom stays off until the user clicks the map, so the map doesn't hijack page scrolling. */
function EnableWheelOnClick() {
  const map = useMapEvents({
    click: () => map.scrollWheelZoom.enable(),
    mouseout: () => map.scrollWheelZoom.disable(),
  })
  return null
}

const linkClass = 'text-xs font-medium text-blue-600 hover:underline'

export default function LocationMapImpl({ latitude, longitude, name }: LocationMapProps) {
  if (typeof latitude !== 'number' || typeof longitude !== 'number') return null

  return (
    <div className="flex flex-col gap-2">
      <div className="relative z-0 h-56 w-full overflow-hidden rounded-2xl border border-slate-200">
        <MapContainer
          center={[latitude, longitude]}
          zoom={14}
          scrollWheelZoom={false}
          className="h-full w-full"
          aria-label={name ? `Map of ${name}` : 'Map'}
        >
          <TileLayer url={OSM_TILE_URL} attribution={OSM_ATTRIBUTION} maxZoom={19} />
          <MapResizer />
          <EnableWheelOnClick />
          <Marker position={[latitude, longitude]} icon={pinIcon} />
        </MapContainer>
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1">
        <a href={googleMapsLink(latitude, longitude)} target="_blank" rel="noopener noreferrer" className={linkClass}>
          Open in Google Maps
        </a>
        <a
          href={googleDirectionsLink(latitude, longitude)}
          target="_blank"
          rel="noopener noreferrer"
          className={linkClass}
        >
          Get directions
        </a>
      </div>
    </div>
  )
}
