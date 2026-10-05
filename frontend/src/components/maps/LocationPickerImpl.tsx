import { useEffect, useRef, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import Button from '../ui/Button'
import {
  MapResizer,
  OSM_ATTRIBUTION,
  OSM_TILE_URL,
  pinIcon,
  SRI_LANKA_CENTER,
  SRI_LANKA_ZOOM,
  type LatLng,
} from './mapShared'

export interface LocationPickerProps {
  value: LatLng | null
  onChange: (value: LatLng) => void
  /** Where to centre the map while nothing is selected (e.g. the hotel's destination). */
  defaultCenter?: LatLng | null
}

interface SearchResult {
  place_id: number
  display_name: string
  lat: string
  lon: string
}

const round = (n: number) => Math.round(n * 1e6) / 1e6
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

function ClickToPlace({ onChange }: { onChange: (v: LatLng) => void }) {
  useMapEvents({ click: (e) => onChange({ lat: round(e.latlng.lat), lng: round(e.latlng.lng) }) })
  return null
}

/** Moves the view when the target changes: flies to searched places, keeps typed coordinates visible. */
function ViewController({ flyTo, value }: { flyTo: { target: LatLng; zoom: number } | null; value: LatLng | null }) {
  const map = useMap()
  useEffect(() => {
    if (flyTo) map.flyTo([flyTo.target.lat, flyTo.target.lng], flyTo.zoom)
  }, [flyTo, map])
  useEffect(() => {
    if (value && !map.getBounds().contains([value.lat, value.lng])) map.panTo([value.lat, value.lng])
  }, [value, map])
  return null
}

/** Leaflet + OpenStreetMap picker: click the map or drag the marker; search places via Nominatim. */
export default function LocationPickerImpl({ value, onChange, defaultCenter }: LocationPickerProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [flyTo, setFlyTo] = useState<{ target: LatLng; zoom: number } | null>(null)
  const lastRequestAt = useRef(0)
  const canLocate = typeof navigator !== 'undefined' && 'geolocation' in navigator

  const center = value ?? defaultCenter ?? SRI_LANKA_CENTER
  const zoom = value ? 13 : defaultCenter ? 10 : SRI_LANKA_ZOOM

  const pick = (lat: number, lng: number, zoomTo = 14) => {
    const target = { lat: round(lat), lng: round(lng) }
    onChange(target)
    setFlyTo({ target, zoom: zoomTo })
  }

  /** Runs only on Enter / the Search button (never as-you-type), at most one request per second. */
  const search = async () => {
    const q = query.trim()
    if (!q || searching) return
    setSearching(true)
    setMessage(null)
    setResults([])
    try {
      const wait = 1000 - (Date.now() - lastRequestAt.current)
      if (wait > 0) await sleep(wait)
      lastRequestAt.current = Date.now()
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&countrycodes=lk&limit=5&q=${encodeURIComponent(q)}`,
        { headers: { Accept: 'application/json' } },
      )
      if (!response.ok) throw new Error(String(response.status))
      const data = (await response.json()) as SearchResult[]
      if (data.length === 0) setMessage('No places found. Try a different name, or click the map.')
      setResults(data.slice(0, 5))
    } catch {
      setMessage('Place search is unavailable right now. You can still click the map to set the location.')
    } finally {
      setSearching(false)
    }
  }

  const useMyLocation = () => {
    setMessage(null)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => pick(coords.latitude, coords.longitude, 15),
      () => setMessage('We could not get your location. Allow location access, or click the map instead.'),
    )
  }

  return (
    <div className="flex flex-col gap-2">
      {/* Not a <form>: the picker lives inside other forms, and nested forms are invalid HTML. */}
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="text"
          value={query}
          placeholder="Search a place in Sri Lanka"
          aria-label="Search a place"
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              void search()
            }
          }}
          className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
        <Button type="button" variant="secondary" disabled={searching || !query.trim()} onClick={() => void search()}>
          {searching ? 'Searching...' : 'Search'}
        </Button>
        {canLocate && (
          <Button type="button" variant="secondary" onClick={useMyLocation}>
            Use my current location
          </Button>
        )}
      </div>

      {message && <p className="text-sm text-slate-600">{message}</p>}
      {results.length > 0 && (
        <ul className="max-h-44 overflow-y-auto rounded-xl border border-slate-200 bg-white text-sm">
          {results.map((r) => (
            <li key={r.place_id}>
              <button
                type="button"
                onClick={() => {
                  pick(Number(r.lat), Number(r.lon))
                  setResults([])
                }}
                className="w-full px-3 py-2 text-left text-slate-700 hover:bg-slate-50"
              >
                {r.display_name}
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="relative z-0 h-72 w-full overflow-hidden rounded-xl border border-slate-200">
        <MapContainer center={[center.lat, center.lng]} zoom={zoom} className="h-full w-full">
          <TileLayer url={OSM_TILE_URL} attribution={OSM_ATTRIBUTION} maxZoom={19} />
          <MapResizer />
          <ClickToPlace onChange={onChange} />
          <ViewController flyTo={flyTo} value={value} />
          {value && (
            <Marker
              position={[value.lat, value.lng]}
              icon={pinIcon}
              draggable
              eventHandlers={{
                dragend: (e) => {
                  const p = (e.target as L.Marker).getLatLng()
                  onChange({ lat: round(p.lat), lng: round(p.lng) })
                },
              }}
            />
          )}
        </MapContainer>
      </div>
      <p className="text-xs text-slate-500">Click the map or drag the pin to set the exact location.</p>
    </div>
  )
}
