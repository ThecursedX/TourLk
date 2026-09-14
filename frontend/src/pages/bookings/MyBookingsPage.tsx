import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { cancelBooking, getMyBookings, requestReschedule } from '../../api/bookingApi'
import { getMyReviews } from '../../api/reviewApi'
import BookingCard from '../../components/bookings/BookingCard'
import ReviewForm from '../../components/reviews/ReviewForm'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'
import type { BookingResponseDto } from '../../types/booking'
import type { ReviewResponseDto } from '../../types/review'

const RESCHEDULABLE = new Set(['PENDING', 'CONFIRMED', 'RESCHEDULED'])
const CANCELLABLE = new Set(['PENDING', 'CONFIRMED', 'RESCHEDULE_REQUESTED', 'RESCHEDULED'])

export default function MyBookingsPage() {
  const [bookings, setBookings] = useState<BookingResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [rescheduleTargetId, setRescheduleTargetId] = useState<number | null>(null)
  const [rescheduleDate, setRescheduleDate] = useState('')

  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [reviewingId, setReviewingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyBookings()
      .then(setBookings)
      .catch(() => setError('Could not load your bookings. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    getMyReviews()
      .then(setReviews)
      .catch(() => {})
  }, [])

  const hasReview = (bookingId: number) =>
    reviews.some((r) => r.reviewableType === 'TOUR_PACKAGE' && r.sourceBookingId === bookingId)

  const handleCancel = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await cancelBooking(id)
      setBookings((prev) => prev.map((b) => (b.id === id ? updated : b)))
    } catch {
      setActionError('That booking could not be cancelled. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  const openReschedule = (id: number) => {
    setActionError(null)
    setRescheduleTargetId(id)
    setRescheduleDate('')
  }

  const closeReschedule = () => {
    setRescheduleTargetId(null)
    setRescheduleDate('')
  }

  const handleRescheduleSubmit = async (e: FormEvent, id: number) => {
    e.preventDefault()
    if (!rescheduleDate) return

    setActionError(null)
    setBusyId(id)
    try {
      const updated = await requestReschedule(id, { newTravelDate: rescheduleDate })
      setBookings((prev) => prev.map((b) => (b.id === id ? updated : b)))
      closeReschedule()
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setActionError(err.response.data.message)
      } else {
        setActionError('That reschedule request could not be submitted. Please try again.')
      }
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Bookings</h1>
        <p className="mt-1 text-slate-600">Bookings you've made for tour packages.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && bookings.length === 0 && (
        <p className="text-slate-600">You haven't booked any tour packages yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {bookings.map((booking) => {
          const disabled = busyId === booking.id
          const isReschedulingThis = rescheduleTargetId === booking.id

          return (
            <BookingCard
              key={booking.id}
              booking={booking}
              footer={
                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap gap-2">
                    {RESCHEDULABLE.has(booking.status) && !isReschedulingThis && (
                      <Button
                        variant="secondary"
                        disabled={disabled}
                        onClick={() => openReschedule(booking.id)}
                      >
                        Request Reschedule
                      </Button>
                    )}
                    {CANCELLABLE.has(booking.status) && (
                      <Button
                        variant="secondary"
                        disabled={disabled}
                        onClick={() => handleCancel(booking.id)}
                      >
                        Cancel
                      </Button>
                    )}
                    {booking.status === 'COMPLETED' && !hasReview(booking.id) && reviewingId !== booking.id && (
                      <Button variant="secondary" disabled={disabled} onClick={() => setReviewingId(booking.id)}>
                        Leave a Review
                      </Button>
                    )}
                  </div>
                  {reviewingId === booking.id && (
                    <ReviewForm
                      reviewableType="TOUR_PACKAGE"
                      reviewableId={booking.tourPackage.id}
                      sourceBookingId={booking.id}
                      onSuccess={(review) => {
                        setReviews((prev) => [...prev, review])
                        setReviewingId(null)
                      }}
                      onCancel={() => setReviewingId(null)}
                    />
                  )}
                  {isReschedulingThis && (
                    <form
                      onSubmit={(e) => handleRescheduleSubmit(e, booking.id)}
                      className="flex items-end gap-2"
                    >
                      <div className="w-40">
                        <Input
                          id={`reschedule-date-${booking.id}`}
                          label="New travel date"
                          type="date"
                          value={rescheduleDate}
                          onChange={(e) => setRescheduleDate(e.target.value)}
                        />
                      </div>
                      <Button type="submit" disabled={disabled || !rescheduleDate}>
                        Submit
                      </Button>
                      <Button type="button" variant="secondary" onClick={closeReschedule}>
                        Cancel
                      </Button>
                    </form>
                  )}
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
