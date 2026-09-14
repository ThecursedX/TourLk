import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import {
  addReply,
  assignTicket,
  closeTicket,
  getTicketById,
  reopenTicket,
  resolveTicket,
} from '../../api/supportTicketApi'
import { useAuthStore } from '../../auth/authStore'
import ReplyThread from '../../components/support/ReplyThread'
import TicketPriorityBadge from '../../components/support/TicketPriorityBadge'
import TicketStatusBadge from '../../components/support/TicketStatusBadge'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { TicketDetailResponseDto } from '../../types/supportTicket'

export default function TicketDetailPage() {
  const { id } = useParams<{ id: string }>()
  const ticketId = Number(id)
  const user = useAuthStore((state) => state.user)

  const [detail, setDetail] = useState<TicketDetailResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionBusy, setActionBusy] = useState(false)

  const [replyMessage, setReplyMessage] = useState('')
  const [replyError, setReplyError] = useState<string | null>(null)
  const [replying, setReplying] = useState(false)

  const load = () => {
    if (!id) return
    setLoading(true)
    setError(null)
    getTicketById(ticketId)
      .then(setDetail)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 403) {
          setError('You do not have permission to view this ticket.')
        } else if (isAxiosError(err) && err.response?.status === 404) {
          setError('This ticket could not be found.')
        } else {
          setError('Could not load this ticket. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const handleReply = async (e: FormEvent) => {
    e.preventDefault()
    if (!replyMessage.trim()) return

    setReplyError(null)
    setReplying(true)
    try {
      await addReply(ticketId, { message: replyMessage })
      setReplyMessage('')
      load()
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setReplyError(err.response.data.message)
      } else {
        setReplyError('That reply could not be sent. Please try again.')
      }
    } finally {
      setReplying(false)
    }
  }

  const runAction = async (action: () => Promise<unknown>) => {
    setActionError(null)
    setActionBusy(true)
    try {
      await action()
      load()
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setActionBusy(false)
    }
  }

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!detail) return null

  const { ticket, replies } = detail
  const isAdmin = user?.role === 'ADMIN'
  const isRaiser = user?.userId === ticket.raisedById
  const backLink = isAdmin ? '/admin/tickets' : '/support/mine'

  return (
    <div className="flex flex-col gap-4">
      <Link to={backLink} className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to tickets
      </Link>

      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{ticket.subject}</h1>
          <TicketStatusBadge status={ticket.status} />
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
          <span>{ticket.category}</span>
          <TicketPriorityBadge priority={ticket.priority} />
        </div>
        <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-3">
          <div>
            <dt className="text-xs uppercase text-slate-500">Raised by</dt>
            <dd className="text-slate-900">{ticket.raisedByName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Assigned to</dt>
            <dd className="text-slate-900">{ticket.assignedToName ?? 'Unassigned'}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Raised on</dt>
            <dd className="text-slate-900">{new Date(ticket.createdAt).toLocaleDateString()}</dd>
          </div>
        </div>

        {actionError && <p className="text-red-600">{actionError}</p>}

        {isAdmin && (
          <div className="flex flex-wrap gap-2 border-t border-slate-200 pt-4">
            {ticket.status === 'OPEN' && (
              <Button disabled={actionBusy} onClick={() => runAction(() => assignTicket(ticket.id))}>
                Assign to me
              </Button>
            )}
            {(ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS') && (
              <Button disabled={actionBusy} onClick={() => runAction(() => resolveTicket(ticket.id))}>
                Mark Resolved
              </Button>
            )}
            {ticket.status !== 'CLOSED' && (
              <Button
                variant="secondary"
                disabled={actionBusy}
                onClick={() => runAction(() => closeTicket(ticket.id))}
              >
                Close Ticket
              </Button>
            )}
          </div>
        )}

        {(ticket.status === 'RESOLVED' || ticket.status === 'CLOSED') && (isRaiser || isAdmin) && (
          <div className="flex border-t border-slate-200 pt-4">
            <Button variant="secondary" disabled={actionBusy} onClick={() => runAction(() => reopenTicket(ticket.id))}>
              Reopen Ticket
            </Button>
          </div>
        )}
      </Card>

      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Conversation</h2>
        <ReplyThread replies={replies} />
      </div>

      {ticket.status !== 'CLOSED' ? (
        <Card>
          <form onSubmit={handleReply} className="flex flex-col gap-3">
            <label htmlFor="reply" className="text-sm font-medium text-slate-700">
              Reply
            </label>
            <textarea
              id="reply"
              rows={3}
              value={replyMessage}
              onChange={(e) => setReplyMessage(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {replyError && <p className="text-sm text-red-600">{replyError}</p>}
            <Button type="submit" disabled={replying || !replyMessage.trim()} className="self-start">
              {replying ? 'Sending...' : 'Send Reply'}
            </Button>
          </form>
        </Card>
      ) : (
        (isRaiser || isAdmin) && <p className="text-slate-600">This ticket is closed. Reopen it to add a reply.</p>
      )}
    </div>
  )
}
