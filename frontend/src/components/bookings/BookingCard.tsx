import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { BookingResponseDto } from '../../types/booking'
import Card from '../ui/Card'
import BookingStatusBadge from './BookingStatusBadge'

interface BookingCardProps {
  booking: BookingResponseDto
  footer?: ReactNode
}

export default function BookingCard({ booking, footer }: BookingCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{booking.tourPackage.title}</h3>
        <BookingStatusBadge status={booking.status} />
      </div>
      <p className="text-sm text-slate-600">{booking.tourPackage.destination}</p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>Travel date: {booking.travelDate}</span>
        <span>
          {booking.numberOfTravelers} traveler{booking.numberOfTravelers === 1 ? '' : 's'}
        </span>
      </div>
      {booking.status === 'RESCHEDULE_REQUESTED' && booking.requestedTravelDate && (
        <p className="text-sm text-sky-700">Requested new date: {booking.requestedTravelDate}</p>
      )}
      {booking.previousTravelDate && (
        <p className="text-xs text-slate-500">Originally booked for {booking.previousTravelDate}</p>
      )}
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link
          to={`/bookings/${booking.id}`}
          className="text-sm font-medium text-blue-600 hover:underline"
        >
          View details
        </Link>
        {footer}
      </div>
    </Card>
  )
}
