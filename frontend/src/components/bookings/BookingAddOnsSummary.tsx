import { Link } from 'react-router-dom'
import ReservationStatusBadge from '../accommodations/ReservationStatusBadge'
import HireStatusBadge from '../vehicles/HireStatusBadge'
import type { BookingResponseDto } from '../../types/booking'

const money = (n: number) => n.toLocaleString(undefined, { style: 'currency', currency: 'USD' })

interface BookingAddOnsSummaryProps {
  booking: BookingResponseDto
  /** Hide the price breakdown (used by compact cards). */
  compact?: boolean
}

/** The hotel rooms and vehicles included in a package booking, with their status and the price breakdown. */
export default function BookingAddOnsSummary({ booking, compact = false }: BookingAddOnsSummaryProps) {
  const reservations = booking.roomReservations ?? []
  const hires = booking.vehicleHires ?? []
  if (reservations.length === 0 && hires.length === 0) return null

  return (
    <div className="flex flex-col gap-2 border-t border-slate-200 pt-3">
      <h3 className="text-sm font-semibold text-slate-900">Included add-ons</h3>
      <ul className="flex flex-col gap-1.5">
        {reservations.map((r) => (
          <li key={`room-${r.id}`} className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-700">
            <span>
              <Link to={`/accommodations/${r.room.accommodationId}`} className="font-medium text-blue-600 hover:underline">
                {r.room.accommodationName}
              </Link>{' '}
              &middot; {r.numberOfRooms} &times; {r.room.roomType} &middot; {r.checkInDate} &rarr; {r.checkOutDate}
            </span>
            <ReservationStatusBadge status={r.status} />
          </li>
        ))}
        {hires.map((h) => (
          <li key={`hire-${h.id}`} className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-700">
            <span>
              <Link to={`/vehicles/${h.vehicle.id}`} className="font-medium text-blue-600 hover:underline">
                {h.vehicle.make} {h.vehicle.model}
              </Link>{' '}
              &middot; {h.startDate} &rarr; {h.endDate}
            </span>
            <HireStatusBadge status={h.status} />
          </li>
        ))}
      </ul>
      {!compact && (
        <dl className="mt-1 flex flex-col gap-0.5 text-sm text-slate-700">
          <div className="flex justify-between">
            <dt>Package</dt>
            <dd>{money(booking.packageSubtotal)}</dd>
          </div>
          {reservations.length > 0 && (
            <div className="flex justify-between">
              <dt>Hotel rooms</dt>
              <dd>{money(booking.roomsSubtotal)}</dd>
            </div>
          )}
          {hires.length > 0 && (
            <div className="flex justify-between">
              <dt>Vehicles</dt>
              <dd>{money(booking.vehiclesSubtotal)}</dd>
            </div>
          )}
          <div className="flex justify-between border-t border-slate-200 pt-1 font-semibold text-slate-900">
            <dt>Total</dt>
            <dd>{money(booking.totalPrice)}</dd>
          </div>
        </dl>
      )}
    </div>
  )
}
