import { useEffect, useState } from 'react'
import { cancelReservation, getReservationCancellationPreview } from '../../api/roomReservationApi'
import { apiErrorMessage } from '../../utils/errors'
import Button from '../ui/Button'
import type { RoomReservationResponseDto } from '../../types/accommodation'
import type { CancellationPreviewResponseDto } from '../../types/booking'

interface CancelReservationPanelProps {
  reservationId: number
  onCancelled: (updated: RoomReservationResponseDto) => void
  onClose: () => void
}

/** Inline "are you sure?" panel: loads the refund preview first, then confirms the cancellation. */
export default function CancelReservationPanel({
  reservationId,
  onCancelled,
  onClose,
}: CancelReservationPanelProps) {
  const [preview, setPreview] = useState<CancellationPreviewResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    getReservationCancellationPreview(reservationId)
      .then(setPreview)
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the refund preview. Please try again.')))
      .finally(() => setLoading(false))
  }, [reservationId])

  const handleConfirm = async () => {
    setError(null)
    setConfirming(true)
    try {
      onCancelled(await cancelReservation(reservationId))
    } catch (err) {
      setError(apiErrorMessage(err, 'This reservation could not be cancelled. Please try again.'))
    } finally {
      setConfirming(false)
    }
  }

  return (
    <div className="flex w-full min-w-0 flex-col gap-2 overflow-hidden rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-sm font-medium text-slate-700">Cancel this reservation?</p>

      {loading && <p className="text-sm text-slate-600">Checking refund amount...</p>}

      {preview && (
        <div className="break-words text-sm text-slate-700">
          {preview.hasPayment ? (
            <p>
              You'll be refunded{' '}
              <span className="font-semibold">
                {preview.refundAmount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
              </span>{' '}
              ({preview.refundPercent}%).
            </p>
          ) : (
            <p>There's no completed payment on this reservation, so nothing will be refunded.</p>
          )}
          <p className="mt-1 break-words text-xs text-slate-500">{preview.ruleText}</p>
        </div>
      )}

      {error && <p className="break-words text-sm text-red-600">{error}</p>}

      <div className="flex flex-wrap gap-2">
        <Button className="whitespace-nowrap" disabled={confirming || loading} onClick={handleConfirm}>
          {confirming ? 'Cancelling...' : 'Confirm Cancellation'}
        </Button>
        <Button type="button" variant="secondary" className="whitespace-nowrap" disabled={confirming} onClick={onClose}>
          Keep Reservation
        </Button>
      </div>
    </div>
  )
}
