import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { SupportTicketResponseDto } from '../../types/supportTicket'
import Card from '../ui/Card'
import TicketPriorityBadge from './TicketPriorityBadge'
import TicketStatusBadge from './TicketStatusBadge'

interface TicketCardProps {
  ticket: SupportTicketResponseDto
  footer?: ReactNode
}

export default function TicketCard({ ticket, footer }: TicketCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{ticket.subject}</h3>
        <TicketStatusBadge status={ticket.status} />
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
        <span>{ticket.category}</span>
        <TicketPriorityBadge priority={ticket.priority} />
      </div>
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>Raised by {ticket.raisedByName}</span>
        <span>{new Date(ticket.createdAt).toLocaleDateString()}</span>
      </div>
      {ticket.assignedToName && <p className="text-xs text-slate-500">Assigned to {ticket.assignedToName}</p>}
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link to={`/support/${ticket.id}`} className="text-sm font-medium text-blue-600 hover:underline">
          View ticket
        </Link>
        {footer}
      </div>
    </Card>
  )
}
