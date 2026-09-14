import type { TicketPriority } from '../../types/supportTicket'

const PRIORITY_CLASSES: Record<TicketPriority, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-100 text-sky-800',
  HIGH: 'bg-orange-100 text-orange-800',
  URGENT: 'bg-red-100 text-red-700',
}

interface TicketPriorityBadgeProps {
  priority: TicketPriority
}

export default function TicketPriorityBadge({ priority }: TicketPriorityBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${PRIORITY_CLASSES[priority]}`}
    >
      {priority}
    </span>
  )
}
