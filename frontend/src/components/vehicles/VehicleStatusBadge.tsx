import type { VehicleStatus } from '../../types/vehicle'

const STATUS_CLASSES: Record<VehicleStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  PENDING_VERIFICATION: 'bg-amber-100 text-amber-800',
  AVAILABLE: 'bg-green-100 text-green-800',
  BOOKED: 'bg-blue-100 text-blue-800',
  UNDER_MAINTENANCE: 'bg-orange-100 text-orange-800',
  OUT_OF_SERVICE: 'bg-slate-200 text-slate-600',
  ARCHIVED: 'bg-red-100 text-red-700',
}

const STATUS_LABELS: Record<VehicleStatus, string> = {
  DRAFT: 'Draft',
  PENDING_VERIFICATION: 'Pending verification',
  AVAILABLE: 'Available',
  BOOKED: 'Booked',
  UNDER_MAINTENANCE: 'Under maintenance',
  OUT_OF_SERVICE: 'Out of service',
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
