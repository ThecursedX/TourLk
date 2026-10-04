import { useEffect } from 'react'
import L from 'leaflet'
import { useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

export interface LatLng {
  lat: number
  lng: number
}

/** Centre of Sri Lanka — the default view when nothing is selected. */
export const SRI_LANKA_CENTER: LatLng = { lat: 7.87, lng: 80.77 }
export const SRI_LANKA_ZOOM = 7

export const OSM_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
export const OSM_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'

/**
 * A divIcon with an inline SVG pin. Avoids Leaflet's default marker images, whose URLs Vite breaks
 * (the well-known "missing marker icon" problem), and needs no image assets.
 */
export const pinIcon = L.divIcon({
  className: '',
  html: `<svg width="30" height="40" viewBox="0 0 30 40" xmlns="http://www.w3.org/2000/svg" style="filter:drop-shadow(0 2px 2px rgba(0,0,0,.35))">
    <path d="M15 1C7.8 1 2 6.8 2 14c0 9.6 13 25 13 25s13-15.4 13-25C28 6.8 22.2 1 15 1z" fill="#2563eb" stroke="#fff" stroke-width="2"/>
    <circle cx="15" cy="14" r="5" fill="#fff"/></svg>`,
  iconSize: [30, 40],
  iconAnchor: [15, 39],
  popupAnchor: [0, -36],
})

/**
 * Re-measures the map whenever its container changes size. A map first rendered in a hidden tab,
 * modal or collapsed section has a 0-size container, which leaves grey tiles until invalidateSize runs.
 */
export function MapResizer() {
  const map = useMap()
  useEffect(() => {
    const container = map.getContainer()
    const refresh = () => map.invalidateSize()
    const timer = setTimeout(refresh, 0)
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(refresh) : null
    observer?.observe(container)
    return () => {
      clearTimeout(timer)
      observer?.disconnect()
    }
  }, [map])
  return null
}

export function googleMapsLink(lat: number, lng: number) {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`
}

export function googleDirectionsLink(lat: number, lng: number) {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`
}
