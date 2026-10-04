import type { DestinationStatus } from '../../types/destination'

const STATUS_CLASSES: Record<DestinationStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  PENDING_REVIEW: 'bg-amber-100 text-amber-800',
  PUBLISHED: 'bg-green-100 text-green-800',
  TEMPORARILY_CLOSED: 'bg-orange-100 text-orange-800',
  INACTIVE: 'bg-slate-200 text-slate-600',
  ARCHIVED: 'bg-red-100 text-red-700',
}

const STATUS_LABELS: Record<DestinationStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  PUBLISHED: 'Published',
  TEMPORARILY_CLOSED: 'Temporarily closed',
  INACTIVE: 'Inactive',
  ARCHIVED: 'Archived',
}

export default function DestinationStatusBadge({ status }: { status: DestinationStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  )
}
