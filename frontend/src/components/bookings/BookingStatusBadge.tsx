import type { BookingStatus } from '../../types/booking'

const STATUS_CLASSES: Record<BookingStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  RESCHEDULE_REQUESTED: 'bg-sky-100 text-sky-800',
  RESCHEDULED: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-slate-200 text-slate-700',
  CANCELLED: 'bg-red-100 text-red-700',
}

const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  RESCHEDULE_REQUESTED: 'Reschedule requested',
  RESCHEDULED: 'Rescheduled',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

interface BookingStatusBadgeProps {
  status: BookingStatus
}

export default function BookingStatusBadge({ status }: BookingStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
