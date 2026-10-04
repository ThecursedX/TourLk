import type { AccommodationStatus } from '../../types/accommodation'

const STATUS_CLASSES: Record<AccommodationStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  PENDING_APPROVAL: 'bg-amber-100 text-amber-800',
  ACTIVE: 'bg-green-100 text-green-800',
  FULLY_BOOKED: 'bg-blue-100 text-blue-800',
  TEMPORARILY_UNAVAILABLE: 'bg-orange-100 text-orange-800',
  INACTIVE: 'bg-slate-200 text-slate-600',
  ARCHIVED: 'bg-red-100 text-red-700',
}

const STATUS_LABELS: Record<AccommodationStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  ACTIVE: 'Active',
  FULLY_BOOKED: 'Fully booked',
  TEMPORARILY_UNAVAILABLE: 'Temporarily unavailable',
  INACTIVE: 'Inactive',
  ARCHIVED: 'Archived',
}

interface AccommodationStatusBadgeProps {
  status: AccommodationStatus
}

export default function AccommodationStatusBadge({ status }: AccommodationStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
