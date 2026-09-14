import { useEffect, useState } from 'react'
import { assignTicket, getUnassignedTickets } from '../../api/supportTicketApi'
import TicketCard from '../../components/support/TicketCard'
import Button from '../../components/ui/Button'
import type { SupportTicketResponseDto } from '../../types/supportTicket'

export default function AdminUnassignedTicketsPage() {
  const [tickets, setTickets] = useState<SupportTicketResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getUnassignedTickets()
      .then(setTickets)
      .catch(() => setError('Could not load unassigned tickets. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleAssign = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      await assignTicket(id)
      setTickets((prev) => prev.filter((t) => t.id !== id))
    } catch {
      setActionError('That ticket could not be assigned. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Unassigned Tickets</h1>
        <p className="mt-1 text-slate-600">Tickets nobody has claimed yet.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && tickets.length === 0 && (
        <p className="text-slate-600">No unassigned tickets right now.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tickets.map((ticket) => (
          <TicketCard
            key={ticket.id}
            ticket={ticket}
            footer={
              <Button disabled={busyId === ticket.id} onClick={() => handleAssign(ticket.id)}>
                Assign to me
              </Button>
            }
          />
        ))}
      </div>
    </div>
  )
}
