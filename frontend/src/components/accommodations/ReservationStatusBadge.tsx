import type { RoomReservationStatus } from '../../types/accommodation'

const STATUS_CLASSES: Record<RoomReservationStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-700',
  COMPLETED: 'bg-slate-200 text-slate-700',
}

const STATUS_LABELS: Record<RoomReservationStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
}

interface ReservationStatusBadgeProps {
  status: RoomReservationStatus
}

export default function ReservationStatusBadge({ status }: ReservationStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
