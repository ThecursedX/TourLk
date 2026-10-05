import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyReservations } from '../../api/roomReservationApi'
import { getMyReviews } from '../../api/reviewApi'
import CancelReservationPanel from '../../components/accommodations/CancelReservationPanel'
import ReservationCard from '../../components/accommodations/ReservationCard'
import ReviewForm from '../../components/reviews/ReviewForm'
import Button from '../../components/ui/Button'
import type { RoomReservationResponseDto } from '../../types/accommodation'
import type { ReviewResponseDto } from '../../types/review'
import { apiErrorMessage } from '../../utils/errors'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED'])

export default function MyReservationsPage() {
  const [reservations, setReservations] = useState<RoomReservationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)

  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [reviewingId, setReviewingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyReservations()
      .then(setReservations)
      .catch((err) => setError(apiErrorMessage(err, 'Could not load your reservations. Please try again later.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    getMyReviews()
      .then(setReviews)
      .catch(() => {})
  }, [])

  const hasReview = (reservationId: number, accommodationId: number) =>
    reviews.some(
      (r) =>
        r.reviewableType === 'ACCOMMODATION' &&
        (r.sourceBookingId === reservationId || r.reviewableId === accommodationId),
    )

  const handleCancelled = (updated: RoomReservationResponseDto) => {
    setReservations((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
    setCancellingId(null)
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Reservations</h1>
        <p className="mt-1 text-slate-600">Rooms you've reserved.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && reservations.length === 0 && (
        <p className="text-slate-600">You haven't reserved any rooms yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 [&>*]:min-w-0">
        {reservations.map((reservation) => (
          <ReservationCard
            key={reservation.id}
            reservation={reservation}
            footer={
              <div className="flex min-w-0 flex-col gap-2">
                {reservation.bookingId && (
                  <p className="text-xs text-slate-500">Paid and managed with your package booking.</p>
                )}
                <div className="flex flex-wrap gap-2">
                  {reservation.status === 'PENDING' && !reservation.bookingId && (
                    <Link to={`/checkout/reservation/${reservation.id}`}>
                      <Button>Pay Now</Button>
                    </Link>
                  )}
                  {CANCELLABLE.has(reservation.status) &&
                    !reservation.bookingId &&
                    cancellingId !== reservation.id && (
                      <Button variant="secondary" onClick={() => setCancellingId(reservation.id)}>
                        Cancel
                      </Button>
                    )}
                  {reservation.status === 'COMPLETED' &&
                    !hasReview(reservation.id, reservation.room.accommodationId) &&
                    reviewingId !== reservation.id && (
                      <Button variant="secondary" onClick={() => setReviewingId(reservation.id)}>
                        Leave a Review
                      </Button>
                    )}
                </div>
                {cancellingId === reservation.id && (
                  <CancelReservationPanel
                    reservationId={reservation.id}
                    onCancelled={handleCancelled}
                    onClose={() => setCancellingId(null)}
                  />
                )}
                {reviewingId === reservation.id && (
                  <ReviewForm
                    reviewableType="ACCOMMODATION"
                    reviewableId={reservation.room.accommodationId}
                    sourceBookingId={reservation.id}
                    onSuccess={(review) => {
                      setReviews((prev) => [...prev, review])
                      setReviewingId(null)
                    }}
                    onCancel={() => setReviewingId(null)}
                  />
                )}
              </div>
            }
          />
        ))}
      </div>
    </div>
  )
}
