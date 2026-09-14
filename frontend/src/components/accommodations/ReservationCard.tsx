import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { RoomReservationResponseDto } from '../../types/accommodation'
import Card from '../ui/Card'
import ReservationStatusBadge from './ReservationStatusBadge'

interface ReservationCardProps {
  reservation: RoomReservationResponseDto
  footer?: ReactNode
}

export default function ReservationCard({ reservation, footer }: ReservationCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{reservation.room.accommodationName}</h3>
        <ReservationStatusBadge status={reservation.status} />
      </div>
      <p className="text-sm text-slate-600">
        {reservation.room.roomType} · {reservation.room.accommodationLocation}
      </p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>
          {reservation.checkInDate} &rarr; {reservation.checkOutDate}
        </span>
        <span>
          {reservation.numberOfRooms} room{reservation.numberOfRooms === 1 ? '' : 's'}
        </span>
      </div>
      <div className="flex items-center justify-between gap-2 pt-1">
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
