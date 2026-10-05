import { lazy, Suspense } from 'react'
import type { LocationPickerProps } from './LocationPickerImpl'

// Leaflet is browser-only and large: load it in its own chunk, only when a picker is shown.
const Impl = lazy(() => import('./LocationPickerImpl'))

export type { LatLng } from './mapShared'

export default function LocationPicker(props: LocationPickerProps) {
  return (
    <Suspense fallback={<div className="h-72 w-full animate-pulse rounded-xl bg-slate-100" />}>
      <Impl {...props} />
    </Suspense>
  )
}
