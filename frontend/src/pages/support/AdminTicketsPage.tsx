import { useEffect, useState } from 'react'
import { assignTicket, closeTicket, getAllTickets, resolveTicket } from '../../api/supportTicketApi'
import TicketCard from '../../components/support/TicketCard'
import Button from '../../components/ui/Button'
import type { SupportTicketResponseDto, TicketStatus } from '../../types/supportTicket'

const STATUS_TABS: { label: string; value: TicketStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Open', value: 'OPEN' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
]

export default function AdminTicketsPage() {
  const [tickets, setTickets] = useState<SupportTicketResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [statusFilter, setStatusFilter] = useState<TicketStatus | undefined>(undefined)

  const load = (status?: TicketStatus) => {
    setLoading(true)
    setError(null)
    getAllTickets(status)
      .then(setTickets)
      .catch(() => setError('Could not load tickets. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load(statusFilter)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const runAction = async (id: number, action: (id: number) => Promise<SupportTicketResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setTickets((prev) => prev.map((t) => (t.id === id ? updated : t)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Support Tickets</h1>
        <p className="mt-1 text-slate-600">All support tickets raised on the platform.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => (
          <Button
            key={tab.label}
            variant={statusFilter === tab.value ? 'primary' : 'secondary'}
            onClick={() => setStatusFilter(tab.value)}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && tickets.length === 0 && <p className="text-slate-600">No tickets found.</p>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tickets.map((ticket) => {
          const disabled = busyId === ticket.id
          return (
            <TicketCard
              key={ticket.id}
              ticket={ticket}
              footer={
                <div className="flex flex-wrap gap-2">
                  {ticket.status === 'OPEN' && (
                    <Button disabled={disabled} onClick={() => runAction(ticket.id, assignTicket)}>
                      Assign to me
                    </Button>
                  )}
                  {(ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS') && (
                    <Button disabled={disabled} onClick={() => runAction(ticket.id, resolveTicket)}>
                      Resolve
                    </Button>
                  )}
                  {ticket.status !== 'CLOSED' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(ticket.id, closeTicket)}
                    >
                      Close
                    </Button>
                  )}
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
