import type { DestinationResponseDto } from '../../types/destination'

interface DestinationClosureBannerProps {
  destination: Pick<DestinationResponseDto, 'status' | 'closureReason' | 'closureUntil'>
  /** Compact single-line variant for list cards. */
  compact?: boolean
}

/** Warning shown while a destination is TEMPORARILY_CLOSED; renders nothing otherwise. */
export default function DestinationClosureBanner({ destination, compact = false }: DestinationClosureBannerProps) {
  if (destination.status !== 'TEMPORARILY_CLOSED') return null

  const until = destination.closureUntil ? new Date(`${destination.closureUntil}T00:00:00`).toLocaleDateString() : null

  if (compact) {
    return (
        <p className="rounded-lg bg-orange-50 px-2.5 py-1.5 text-xs font-medium text-orange-800" role="status">
          ⚠ Temporarily closed{until ? ` until ${until}` : ''}
        </p>
    )
  }

  return (
      <div className="rounded-lg border border-orange-300 bg-orange-50 px-4 py-3 text-sm text-orange-900" role="alert">
        <p className="font-semibold">⚠ This destination is temporarily closed{until ? ` until ${until}` : ''}.</p>
        {destination.closureReason && <p className="mt-1">{destination.closureReason}</p>}
        <p className="mt-1 text-orange-800">Please check before you plan a visit.</p>
      </div>
  )
}
