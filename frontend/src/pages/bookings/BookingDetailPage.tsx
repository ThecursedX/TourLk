import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getBookingById } from '../../api/bookingApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import BookingStatusBadge from '../../components/bookings/BookingStatusBadge'
import type { BookingResponseDto } from '../../types/booking'

export default function BookingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [booking, setBooking] = useState<BookingResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <div className="flex flex-col gap-4">
      <Link to="/bookings/mine" className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to my bookings
      </Link>
      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{booking.tourPackage.title}</h1>
          <BookingStatusBadge status={booking.status} />
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
            <dt className="text-xs uppercase text-slate-500">Price</dt>
            <dd className="text-slate-900">
              {booking.tourPackage.price.toLocaleString(undefined, {
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
        {booking.requestedTravelDate && (
          <p className="text-sm text-sky-700">
            Requested new travel date: {booking.requestedTravelDate} (awaiting admin decision)
          </p>
        )}
        {booking.previousTravelDate && (
          <p className="text-sm text-slate-500">Originally booked for {booking.previousTravelDate}</p>
        )}
        {booking.specialRequests && (
          <div>
            <dt className="text-xs uppercase text-slate-500">Special requests</dt>
            <dd className="whitespace-pre-line text-slate-700">{booking.specialRequests}</dd>
          </div>
        )}
        {booking.status === 'PENDING' && (
          <Link to={`/checkout/booking/${booking.id}`} className="self-start">
            <Button>Pay Now</Button>
          </Link>
        )}
      </Card>
    </div>
  )
}
