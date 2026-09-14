import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyTickets } from '../../api/supportTicketApi'
import TicketCard from '../../components/support/TicketCard'
import Button from '../../components/ui/Button'
import type { SupportTicketResponseDto } from '../../types/supportTicket'

export default function MyTicketsPage() {
  const [tickets, setTickets] = useState<SupportTicketResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getMyTickets()
      .then(setTickets)
      .catch(() => setError('Could not load your tickets. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">My Support Tickets</h1>
          <p className="mt-1 text-slate-600">Tickets you've raised with support.</p>
        </div>
        <Link to="/support/new">
          <Button>New Ticket</Button>
        </Link>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && tickets.length === 0 && (
        <p className="text-slate-600">You haven't raised any support tickets yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tickets.map((ticket) => (
          <TicketCard key={ticket.id} ticket={ticket} />
        ))}
      </div>
    </div>
  )
}
