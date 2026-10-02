import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import {
  archiveDestination,
  closeDestination,
  createDestination,
  deactivateDestination,
  getAllDestinations,
  publishDestination,
  reactivateDestination,
  reopenDestination,
  submitDestinationForReview,
  updateDestination,
} from '../../api/destinationApi'
import DestinationForm from '../../components/destinations/DestinationForm'
import DestinationStatusBadge from '../../components/destinations/DestinationStatusBadge'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'
import { formatProvince, type DestinationRequestDto, type DestinationResponseDto } from '../../types/destination'

export default function AdminDestinationsPage() {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)

  // Closing needs a reason (and optionally an end date), so it has its own inline form.
  const [closingId, setClosingId] = useState<number | null>(null)
  const [closeReason, setCloseReason] = useState('')
  const [closeUntil, setCloseUntil] = useState('')
  const [closeError, setCloseError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getAllDestinations()
      .then(setDestinations)
      .catch(() => setError('Could not load destinations. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleCreate = async (values: DestinationRequestDto) => {
    await createDestination(values)
    setCreating(false)
    load()
  }

  const handleUpdate = async (id: number, values: DestinationRequestDto) => {
    await updateDestination(id, values)
    setEditingId(null)
    load()
  }

  const runAction = async (id: number, action: (id: number) => Promise<DestinationResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setDestinations((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
    } catch (err) {
      setActionError(
        isAxiosError<ErrorResponse>(err) && err.response?.data?.message
          ? err.response.data.message
          : 'That action could not be completed. Please try again.',
      )
    } finally {
      setBusyId(null)
    }
  }

  const startClosing = (id: number) => {
    setClosingId(id)
    setCloseReason('')
    setCloseUntil('')
    setCloseError(null)
  }

  const handleClose = async (e: FormEvent, id: number) => {
    e.preventDefault()
    if (!closeReason.trim()) {
      setCloseError('A closure reason is required')
      return
    }
    setCloseError(null)
    setBusyId(id)
    try {
      const updated = await closeDestination(id, { reason: closeReason.trim(), until: closeUntil || undefined })
      setDestinations((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
      setClosingId(null)
    } catch (err) {
      setCloseError(
        isAxiosError<ErrorResponse>(err) && err.response?.data?.message
          ? err.response.data.message
          : 'The destination could not be closed. Please try again.',
      )
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Manage Destinations</h1>
          <p className="mt-1 text-slate-600">
            Curate the list of places tourists can browse and filter by.
          </p>
        </div>
        {!creating && <Button onClick={() => setCreating(true)}>New Destination</Button>}
      </div>

      {creating && (
        <Card>
          <h2 className="mb-4 text-lg font-semibold text-slate-900">New Destination</h2>
          <DestinationForm
            onSubmit={handleCreate}
            onCancel={() => setCreating(false)}
            submitLabel="Create Destination"
            submittingLabel="Creating..."
          />
        </Card>
      )}

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && destinations.length === 0 && (
        <p className="text-slate-600">No destinations yet. Create the first one above.</p>
      )}

      {!loading && !error && destinations.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Province / District</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Packages</th>
                <th className="px-4 py-3 text-right">Hotels</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {destinations.map((destination) => {
                const busy = busyId === destination.id
                if (editingId === destination.id) {
                  return (
                    <tr key={destination.id}>
                      <td colSpan={7} className="bg-slate-50 px-4 py-4">
                        <DestinationForm
                          initialValues={{
                            name: destination.name,
                            description: destination.description ?? '',
                            province: destination.province,
                            district: destination.district,
                            category: destination.category,
                            bestTimeToVisit: destination.bestTimeToVisit ?? '',
                            imageUrls: destination.imageUrls,
                            openingHours: destination.openingHours ?? '',
                            entryFee: destination.entryFee,
                            visitorRules: destination.visitorRules ?? '',
                            latitude: destination.latitude,
                            longitude: destination.longitude,
                          }}
                          onSubmit={(values) => handleUpdate(destination.id, values)}
                          onCancel={() => setEditingId(null)}
                          submitLabel="Save Changes"
                          submittingLabel="Saving..."
                        />
                      </td>
                    </tr>
                  )
                }

                const status = destination.status
                return [
                  <tr key={destination.id} className="text-slate-700">
                    <td className="px-4 py-3 font-medium text-slate-900">{destination.name}</td>
                    <td className="px-4 py-3">
                      {formatProvince(destination.province)} / {destination.district}
                    </td>
                    <td className="px-4 py-3">{destination.category}</td>
                    <td className="px-4 py-3">
                      <DestinationStatusBadge status={status} />
                      {status === 'TEMPORARILY_CLOSED' && destination.closureReason && (
                        <p className="mt-1 max-w-xs text-xs text-slate-500">
                          {destination.closureReason}
                          {destination.closureUntil ? ` (until ${destination.closureUntil})` : ''}
                        </p>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">{destination.activePackageCount ?? '—'}</td>
                    <td className="px-4 py-3 text-right">{destination.activeAccommodationCount ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button variant="secondary" disabled={busy} onClick={() => setEditingId(destination.id)}>
                          Edit
                        </Button>
                        {status === 'DRAFT' && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => runAction(destination.id, submitDestinationForReview)}
                          >
                            Submit for review
                          </Button>
                        )}
                        {(status === 'DRAFT' || status === 'PENDING_REVIEW') && (
                          <Button disabled={busy} onClick={() => runAction(destination.id, publishDestination)}>
                            Publish
                          </Button>
                        )}
                        {(status === 'PUBLISHED' || status === 'TEMPORARILY_CLOSED') && (
                          <Button variant="secondary" disabled={busy} onClick={() => startClosing(destination.id)}>
                            {status === 'PUBLISHED' ? 'Close temporarily' : 'Update closure'}
                          </Button>
                        )}
                        {status === 'TEMPORARILY_CLOSED' && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => runAction(destination.id, reopenDestination)}
                          >
                            Reopen
                          </Button>
                        )}
                        {(status === 'PUBLISHED' || status === 'TEMPORARILY_CLOSED') && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => runAction(destination.id, deactivateDestination)}
                          >
                            Deactivate
                          </Button>
                        )}
                        {(status === 'INACTIVE' || status === 'ARCHIVED') && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => runAction(destination.id, reactivateDestination)}
                          >
                            {status === 'ARCHIVED' ? 'Restore' : 'Reactivate'}
                          </Button>
                        )}
                        {status !== 'ARCHIVED' && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => {
                              if (window.confirm('Archive this destination? It will be hidden from the public.')) {
                                void runAction(destination.id, archiveDestination)
                              }
                            }}
                          >
                            Archive
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>,
                  closingId === destination.id && (
                    <tr key={`${destination.id}-close`}>
                      <td colSpan={7} className="bg-orange-50 px-4 py-4">
                        <form onSubmit={(e) => handleClose(e, destination.id)} className="flex flex-wrap items-end gap-3">
                          <div className="w-80">
                            <Input
                              id={`closeReason-${destination.id}`}
                              label="Closure reason"
                              placeholder="e.g. Bridge repairs"
                              value={closeReason}
                              onChange={(e) => setCloseReason(e.target.value)}
                              maxLength={500}
                            />
                          </div>
                          <Input
                            id={`closeUntil-${destination.id}`}
                            label="Closed until (optional)"
                            type="date"
                            value={closeUntil}
                            onChange={(e) => setCloseUntil(e.target.value)}
                          />
                          <Button type="submit" disabled={busy}>
                            Mark as closed
                          </Button>
                          <Button type="button" variant="secondary" onClick={() => setClosingId(null)}>
                            Cancel
                          </Button>
                        </form>
                        {closeError && <p className="mt-2 text-sm text-red-600">{closeError}</p>}
                      </td>
                    </tr>
                  ),
                ]
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
