import type { PaymentStatus } from '../../types/payment'

const STATUS_CLASSES: Record<PaymentStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  SUCCEEDED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-700',
  REFUNDED: 'bg-slate-200 text-slate-700',
}

const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pending',
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
}

interface PaymentStatusBadgeProps {
  status: PaymentStatus
}

export default function PaymentStatusBadge({ status }: PaymentStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
