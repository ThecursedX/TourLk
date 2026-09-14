import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { cancelReservation, getMyReservations } from '../../api/roomReservationApi'
import { getMyReviews } from '../../api/reviewApi'
import ReservationCard from '../../components/accommodations/ReservationCard'
import ReviewForm from '../../components/reviews/ReviewForm'
import Button from '../../components/ui/Button'
import type { RoomReservationResponseDto } from '../../types/accommodation'
import type { ReviewResponseDto } from '../../types/review'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED'])

export default function MyReservationsPage() {
  const [reservations, setReservations] = useState<RoomReservationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [reviewingId, setReviewingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyReservations()
      .then(setReservations)
      .catch(() => setError('Could not load your reservations. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    getMyReviews()
      .then(setReviews)
      .catch(() => {})
  }, [])

  const hasReview = (reservationId: number) =>
    reviews.some((r) => r.reviewableType === 'ACCOMMODATION' && r.sourceBookingId === reservationId)

  const handleCancel = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await cancelReservation(id)
      setReservations((prev) => prev.map((r) => (r.id === id ? updated : r)))
    } catch {
      setActionError('That reservation could not be cancelled. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Reservations</h1>
        <p className="mt-1 text-slate-600">Rooms you've reserved.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && reservations.length === 0 && (
        <p className="text-slate-600">You haven't reserved any rooms yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {reservations.map((reservation) => (
          <ReservationCard
            key={reservation.id}
            reservation={reservation}
            footer={
              <div className="flex flex-col gap-2">
                <div className="flex gap-2">
                  {reservation.status === 'PENDING' && (
                    <Link to={`/checkout/reservation/${reservation.id}`}>
                      <Button disabled={busyId === reservation.id}>Pay Now</Button>
                    </Link>
                  )}
                  {CANCELLABLE.has(reservation.status) && (
                    <Button
                      variant="secondary"
                      disabled={busyId === reservation.id}
                      onClick={() => handleCancel(reservation.id)}
                    >
                      Cancel
                    </Button>
                  )}
                  {reservation.status === 'COMPLETED' &&
                    !hasReview(reservation.id) &&
                    reviewingId !== reservation.id && (
                      <Button
                        variant="secondary"
                        disabled={busyId === reservation.id}
                        onClick={() => setReviewingId(reservation.id)}
                      >
                        Leave a Review
                      </Button>
                    )}
                </div>
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
