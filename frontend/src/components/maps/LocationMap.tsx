import { lazy, Suspense } from 'react'
import type { LocationMapProps } from './LocationMapImpl'

const Impl = lazy(() => import('./LocationMapImpl'))

/** Read-only map with one marker and plain map links. Renders nothing when coordinates are missing. */
export default function LocationMap(props: LocationMapProps) {
  if (typeof props.latitude !== 'number' || typeof props.longitude !== 'number') return null
  return (
    <Suspense fallback={<div className="h-56 w-full animate-pulse rounded-2xl bg-slate-100" />}>
      <Impl {...props} />
    </Suspense>
  )
}
