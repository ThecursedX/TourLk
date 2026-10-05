import { closureLabel } from '../../utils/closure'

interface DestinationClosureBannerProps {
  destination: {
    status: string
    closureReason?: string | null
    closureFrom?: string | null
    closureUntil?: string | null
  }
  /** Compact single-line variant for list cards. */
  compact?: boolean
}

/**
 * Warning shown while a destination is TEMPORARILY_CLOSED; renders nothing otherwise. Wording depends
 * on which dates exist: "Closed from X until Y", "Closed until Y", "Closed from X" or "Temporarily closed".
 */
export default function DestinationClosureBanner({ destination, compact = false }: DestinationClosureBannerProps) {
  if (destination.status !== 'TEMPORARILY_CLOSED') return null

  const label = closureLabel(destination)

  if (compact) {
    return (
        <p className="rounded-lg bg-orange-50 px-2.5 py-1.5 text-xs font-medium text-orange-800" role="status">
          ⚠ {label}
        </p>
    )
  }

  return (
      <div className="rounded-lg border border-orange-300 bg-orange-50 px-4 py-3 text-sm text-orange-900" role="alert">
        <p className="font-semibold">⚠ {label}.</p>
        {destination.closureReason && <p className="mt-1">{destination.closureReason}</p>}
        <p className="mt-1 text-orange-800">Please check before you plan a visit.</p>
      </div>
  )
}
