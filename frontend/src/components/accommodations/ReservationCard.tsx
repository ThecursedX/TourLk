import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { RoomReservationResponseDto } from '../../types/accommodation'
import Card from '../ui/Card'
import CardImage from '../ui/CardImage'
import ReservationStatusBadge from './ReservationStatusBadge'

interface ReservationCardProps {
  reservation: RoomReservationResponseDto
  footer?: ReactNode
}

export default function ReservationCard({ reservation, footer }: ReservationCardProps) {
  return (
    <Card className="flex min-w-0 flex-col gap-3">
      <div className="flex items-start gap-3">
        <CardImage
          urls={reservation.room.coverImageUrl ? [reservation.room.coverImageUrl] : []}
          alt={reservation.room.accommodationName}
          className="!aspect-auto h-20 w-28 shrink-0"
        />
        <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-2">
          <h3 className="min-w-0 break-words text-lg font-semibold text-slate-900">
            {reservation.room.accommodationName}
          </h3>
          <ReservationStatusBadge status={reservation.status} />
        </div>
      </div>
      {reservation.bookingId && (
        <Link
          to={`/bookings/${reservation.bookingId}`}
          className="self-start rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-medium text-indigo-800 hover:underline"
        >
          Part of package: {reservation.packageTitle}
        </Link>
      )}
      <p className="text-sm text-slate-600">
        {reservation.room.roomType} · {reservation.room.accommodationLocation}
      </p>
      <div className="flex flex-wrap items-center justify-between gap-x-2 text-sm text-slate-700">
        <span>
          {reservation.checkInDate} &rarr; {reservation.checkOutDate}
        </span>
        <span>
          {reservation.numberOfRooms} room{reservation.numberOfRooms === 1 ? '' : 's'}
          {reservation.numberOfGuests != null &&
            ` · ${reservation.numberOfGuests} guest${reservation.numberOfGuests === 1 ? '' : 's'}`}
        </span>
      </div>
      <p className="text-sm font-medium text-slate-800">
        Total: {reservation.totalPrice.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
      </p>
      <div className="flex min-w-0 flex-col gap-2 pt-1">
        <Link
          to={`/accommodations/${reservation.room.accommodationId}`}
          className="text-sm font-medium text-blue-600 hover:underline"
        >
          View property
        </Link>
        {footer}
      </div>
    </Card>
  )
}
