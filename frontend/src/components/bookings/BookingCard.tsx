import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { BookingResponseDto } from '../../types/booking'
import Card from '../ui/Card'
import BookingStatusBadge from './BookingStatusBadge'
import BookingAddOnsSummary from './BookingAddOnsSummary'

interface BookingCardProps {
  booking: BookingResponseDto
  footer?: ReactNode
}

export default function BookingCard({ booking, footer }: BookingCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{booking.tourPackage.title}</h3>
        <div className="flex shrink-0 flex-col items-end gap-1">
          <BookingStatusBadge status={booking.status} />
          {booking.paid && (
            <span className="inline-block rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
              Paid
            </span>
          )}
        </div>
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
      <BookingAddOnsSummary booking={booking} compact />
      <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
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
