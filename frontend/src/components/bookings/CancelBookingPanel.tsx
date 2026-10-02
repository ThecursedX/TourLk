import { useEffect, useState } from 'react'
import { cancelBooking, getCancellationPreview } from '../../api/bookingApi'
import Button from '../ui/Button'
import type { BookingResponseDto, CancellationPreviewResponseDto } from '../../types/booking'

interface CancelBookingPanelProps {
  bookingId: number
  onCancelled: (updated: BookingResponseDto) => void
  onClose: () => void
}

/** Inline "are you sure?" panel — loads the refund preview first, then confirms the cancellation. */
export default function CancelBookingPanel({ bookingId, onCancelled, onClose }: CancelBookingPanelProps) {
  const [preview, setPreview] = useState<CancellationPreviewResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    getCancellationPreview(bookingId)
      .then(setPreview)
      .catch(() => setError('Could not load the refund preview. Please try again.'))
      .finally(() => setLoading(false))
  }, [bookingId])

  const handleConfirm = async () => {
    setError(null)
    setConfirming(true)
    try {
      const updated = await cancelBooking(bookingId)
      onCancelled(updated)
    } catch {
      setError('This booking could not be cancelled. Please try again.')
    } finally {
      setConfirming(false)
    }
  }

  return (
    <div className="flex w-full flex-col gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-sm font-medium text-slate-700">Cancel this booking?</p>

      {loading && <p className="text-sm text-slate-600">Checking refund amount...</p>}

      {preview && (
        <div className="text-sm text-slate-700">
          {preview.hasPayment ? (
            <p>
              You'll be refunded{' '}
              <span className="font-semibold">
                {preview.refundAmount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
              </span>{' '}
              ({preview.refundPercent}%).
            </p>
          ) : (
            <p>There's no completed payment on this booking, so nothing will be refunded.</p>
          )}
          <p className="mt-1 text-xs text-slate-500">{preview.ruleText}</p>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex gap-2">
        <Button disabled={confirming} onClick={handleConfirm}>
          {confirming ? 'Cancelling...' : 'Confirm Cancellation'}
        </Button>
        <Button type="button" variant="secondary" disabled={confirming} onClick={onClose}>
          Keep Booking
        </Button>
      </div>
    </div>
  )
}
