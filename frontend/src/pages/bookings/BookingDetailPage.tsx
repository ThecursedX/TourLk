import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getBookingById } from '../../api/bookingApi'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import BookingStatusBadge from '../../components/bookings/BookingStatusBadge'
import CancelBookingPanel from '../../components/bookings/CancelBookingPanel'
import BookingAddOnsSummary from '../../components/bookings/BookingAddOnsSummary'
import type { BookingResponseDto } from '../../types/booking'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED', 'RESCHEDULE_REQUESTED', 'RESCHEDULED'])

export default function BookingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const user = useAuthStore((state) => state.user)
  const [booking, setBooking] = useState<BookingResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getBookingById(Number(id))
      .then(setBooking)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 404) {
          setError('This booking could not be found.')
        } else if (isAxiosError(err) && err.response?.status === 403) {
          setError('You do not have permission to view this booking.')
        } else {
          setError('Could not load this booking. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!booking) return null

  const isTourist = !!user && user.userId === booking.touristId
  const isAdmin = user?.role === 'ADMIN'
  // A guide viewing a booking on their own package: not the tourist, not an admin.
  const canCancel = (isAdmin || isTourist) && CANCELLABLE.has(booking.status)
  const canPay = isTourist && (booking.status === 'PENDING' || booking.status === 'CONFIRMED') && !booking.paid
  const backLink = isAdmin
    ? { to: '/admin/bookings', label: 'Back to all bookings' }
    : isTourist
      ? { to: '/bookings/mine', label: 'Back to my bookings' }
      : { to: '/packages/mine/bookings', label: 'Back to package bookings' }

  return (
    <div className="flex flex-col gap-4">
      <Link to={backLink.to} className="text-sm font-medium text-blue-600 hover:underline">
        &larr; {backLink.label}
      </Link>
      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{booking.tourPackage.title}</h1>
          <div className="flex shrink-0 items-center gap-2">
            <BookingStatusBadge status={booking.status} />
            {booking.paid && (
              <span className="inline-block rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
                Paid
              </span>
            )}
          </div>
        </div>
        <p className="text-slate-600">{booking.tourPackage.destination}</p>
        <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-4">
          <div>
            <dt className="text-xs uppercase text-slate-500">Travel date</dt>
            <dd className="text-slate-900">{booking.travelDate}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Travelers</dt>
            <dd className="text-slate-900">{booking.numberOfTravelers}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Total price</dt>
            <dd className="text-slate-900">
              {booking.totalPrice.toLocaleString(undefined, {
                style: 'currency',
                currency: 'USD',
              })}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Booked by</dt>
            <dd className="text-slate-900">{booking.touristName}</dd>
          </div>
        </div>
        <BookingAddOnsSummary booking={booking} />
        {booking.requestedTravelDate && (
          <p className="text-sm text-sky-700">
            Requested new travel date: {booking.requestedTravelDate} (awaiting admin decision)
          </p>
        )}
        {booking.previousTravelDate && (
          <p className="text-sm text-slate-500">Originally booked for {booking.previousTravelDate}</p>
        )}
        {booking.status === 'REJECTED' && booking.rejectionReason && (
          <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
            Rejected: {booking.rejectionReason}
          </p>
        )}
        {booking.specialRequests && (
          <div>
            <dt className="text-xs uppercase text-slate-500">Special requests</dt>
            <dd className="whitespace-pre-line text-slate-700">{booking.specialRequests}</dd>
          </div>
        )}
        {canPay && (
          <Link to={`/checkout/booking/${booking.id}`} className="self-start">
            <Button>Pay Now</Button>
          </Link>
        )}
        {canCancel && !cancelling && (
          <Button variant="secondary" className="self-start" onClick={() => setCancelling(true)}>
            Cancel Booking
          </Button>
        )}
        {canCancel && cancelling && (
          <CancelBookingPanel
            bookingId={booking.id}
            onCancelled={(updated) => {
              setBooking(updated)
              setCancelling(false)
            }}
            onClose={() => setCancelling(false)}
          />
        )}
      </Card>
    </div>
  )
}
