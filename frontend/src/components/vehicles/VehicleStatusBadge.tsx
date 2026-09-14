import type { VehicleStatus } from '../../types/vehicle'

const STATUS_CLASSES: Record<VehicleStatus, string> = {
  PENDING_APPROVAL: 'bg-amber-100 text-amber-800',
  ACTIVE: 'bg-green-100 text-green-800',
  INACTIVE: 'bg-slate-200 text-slate-600',
  ARCHIVED: 'bg-red-100 text-red-700',
}

const STATUS_LABELS: Record<VehicleStatus, string> = {
  PENDING_APPROVAL: 'Pending approval',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  ARCHIVED: 'Archived',
}

interface VehicleStatusBadgeProps {
  status: VehicleStatus
}

export default function VehicleStatusBadge({ status }: VehicleStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
