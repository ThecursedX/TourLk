import type { TicketStatus } from '../../types/supportTicket'

const STATUS_CLASSES: Record<TicketStatus, string> = {
  OPEN: 'bg-amber-100 text-amber-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  WAITING_FOR_USER: 'bg-purple-100 text-purple-800',
  RESOLVED: 'bg-green-100 text-green-800',
  CLOSED: 'bg-slate-200 text-slate-600',
  WITHDRAWN: 'bg-slate-100 text-slate-500',
}

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  WAITING_FOR_USER: 'Waiting for you',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  WITHDRAWN: 'Withdrawn',
}

interface TicketStatusBadgeProps {
  status: TicketStatus
}

export default function TicketStatusBadge({ status }: TicketStatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
