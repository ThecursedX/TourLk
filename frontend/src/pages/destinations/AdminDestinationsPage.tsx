import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import {
  archiveDestination,
  closeDestination,
  createDestination,
  deactivateDestination,
  getAllDestinations,
  getClosureImpact,
  publishDestination,
  reactivateDestination,
  reopenDestination,
  submitDestinationForReview,
  updateDestination,
} from '../../api/destinationApi'
import DestinationForm from '../../components/destinations/DestinationForm'
import DestinationStatusBadge from '../../components/destinations/DestinationStatusBadge'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { todayIso } from '../../utils/closure'
import type { ErrorResponse } from '../../types/auth'
import {
  formatProvince,
  type DestinationRequestDto,
  type DestinationResponseDto,
  type ClosureSummaryDto,
  type DestinationStatus,
} from '../../types/destination'

const STATUS_FILTER_OPTIONS: { value: DestinationStatus; label: string }[] = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_REVIEW', label: 'Pending review' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'TEMPORARILY_CLOSED', label: 'Temporarily closed' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'ARCHIVED', label: 'Archived' },
]

/** " (from X until Y)", " (until Y)", " (from X)" or "" for the closure dates under the status badge. */
function closureRange(d: { closureFrom: string | null; closureUntil: string | null }): string {
    if (d.closureFrom && d.closureUntil) return ` (${d.closureFrom} to ${d.closureUntil})`
  if (d.closureUntil) return ` (until ${d.closureUntil})`
  if (d.closureFrom) return ` (from ${d.closureFrom})`
  return ''
}

function Thumbnail({ url, name }: { url?: string; name: string }) {
  const [failed, setFailed] = useState(false)
  if (!url || failed) {
    return (
        <span
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-400"
            aria-hidden="true"
        >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
          <rect x="3" y="4" width="18" height="16" rx="3" />
          <circle cx="9" cy="10" r="1.8" />
          <path d="M4 18l5-5 4 4 3-3 4 4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    )
  }
  return (
      <img
          src={url}
          alt={name}
          className="h-11 w-11 shrink-0 rounded-xl border border-slate-200 object-cover"
          onError={() => setFailed(true)}
      />
  )
}

interface MenuItem {
  label: string
  onClick: () => void
  danger?: boolean
}

/** "..." overflow menu. Positioned `fixed` so the table's horizontal scroll container can't clip it. */
function RowMenu({ items, disabled }: { items: MenuItem[]; disabled?: boolean }) {
  const [pos, setPos] = useState<{ top: number; right: number } | null>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!pos) return
    const close = () => setPos(null)
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', onKey)
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [pos])

  if (items.length === 0) return null

  const toggle = () => {
    if (pos) {
      setPos(null)
      return
    }
    const rect = buttonRef.current?.getBoundingClientRect()
    if (rect) setPos({ top: rect.bottom + 6, right: window.innerWidth - rect.right })
  }

  return (
      <>
        <button
            ref={buttonRef}
            type="button"
            disabled={disabled}
            aria-haspopup="menu"
            aria-expanded={pos !== null}
            aria-label="More actions"
            onMouseDown={(e) => e.stopPropagation()}
            onClick={toggle}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
        >
        <span aria-hidden="true" className="text-lg leading-none">
          &hellip;
        </span>
        </button>
        {pos && (
            <div
                role="menu"
                style={{ top: pos.top, right: pos.right }}
                onMouseDown={(e) => e.stopPropagation()}
                className="fixed z-50 flex min-w-44 flex-col gap-1 rounded-2xl border border-slate-200 bg-white p-2 shadow-soft"
            >
              {items.map((item) => (
                  <button
                      key={item.label}
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setPos(null)
                        item.onClick()
                      }}
                      className={`rounded-xl px-3 py-2 text-left text-sm font-medium hover:bg-slate-50 ${
                          item.danger ? 'text-red-600' : 'text-slate-700'
                      }`}
                  >
                    {item.label}
                  </button>
              ))}
            </div>
        )}
      </>
  )
}

export default function AdminDestinationsPage() {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  // The new-destination card animates open/closed, so the form stays mounted briefly after `creating` clears.
  const [formMounted, setFormMounted] = useState(false)
  const [formKey, setFormKey] = useState(0)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<DestinationStatus | ''>('')

  // Closing needs a reason (and optionally an end date), so it has its own inline form.
  const [closingId, setClosingId] = useState<number | null>(null)
  const [closeReason, setCloseReason] = useState('')
  const [closeFrom, setCloseFrom] = useState('')
  const [closeUntil, setCloseUntil] = useState('')
  const [closeResult, setCloseResult] = useState<{ name: string; summary: ClosureSummaryDto } | null>(null)
  const [closeError, setCloseError] = useState<string | null>(null)

  useEffect(() => {
    if (creating) {
      setFormMounted(true)
      return
    }
    const timer = setTimeout(() => {
      setFormMounted(false)
      setFormKey((k) => k + 1)
    }, 300)
    return () => clearTimeout(timer)
  }, [creating])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return destinations.filter(
        (d) =>
            (!statusFilter || d.status === statusFilter) &&
            (!term ||
                d.name.toLowerCase().includes(term) ||
                d.district.toLowerCase().includes(term) ||
                d.category.toLowerCase().includes(term)),
    )
  }, [destinations, search, statusFilter])

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
    setCloseFrom('')
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
      // Preview first: closing cancels and fully refunds active bookings that overlap the window.
      const impact = await getClosureImpact(id, closeFrom || undefined, closeUntil || undefined)
      if (impact.affectedBookings > 0) {
        const n = impact.affectedBookings
        const ok = window.confirm(
          `${n} existing booking${n === 1 ? '' : 's'} in this period will be cancelled and fully refunded. Continue?`,
        )
        if (!ok) return
      }
      const updated = await closeDestination(id, {
        reason: closeReason.trim(),
        from: closeFrom || undefined,
        until: closeUntil || undefined,
      })
      setDestinations((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
      setClosingId(null)
      if (updated.closureSummary) {
        setCloseResult({ name: updated.name, summary: updated.closureSummary })
      }
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

  const confirmArchive = (id: number) => {
    if (window.confirm('Archive this destination? It will be hidden from the public.')) {
      void runAction(id, archiveDestination)
    }
  }

  return (
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Manage Destinations</h1>
            <p className="mt-1 text-slate-600">Curate the list of places tourists can browse and filter by.</p>
          </div>
          <Button onClick={() => setCreating(true)} disabled={creating}>
            + New Destination
          </Button>
        </div>

        <div
            className={`grid transition-all duration-300 ease-in-out ${
                creating ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
            }`}
            aria-hidden={!creating}
        >
          <div className="overflow-hidden">
            {formMounted && (
                <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-soft">
                  <h2 className="text-lg font-semibold text-slate-900">New Destination</h2>
                  <p className="mb-5 mt-1 text-sm text-slate-600">
                    Add a place for tourists to discover. You can save it as a draft and publish it later.
                  </p>
                  <DestinationForm
                      key={formKey}
                      onSubmit={handleCreate}
                      onCancel={() => setCreating(false)}
                      submitLabel="Create Destination"
                      submittingLabel="Creating..."
                  />
                </div>
            )}
          </div>
        </div>

        {error && <p className="text-red-600">{error}</p>}
        {actionError && <p className="text-red-600">{actionError}</p>}

        {closeResult && (
        <div role="status" className="flex items-start justify-between gap-4 rounded-xl border border-orange-300 bg-orange-50 p-4 text-sm text-orange-900">
          <div>
            <p className="font-semibold">{closeResult.name} is now closed.</p>
            <p className="mt-1">
              {closeResult.summary.cancelledBookings} booking{closeResult.summary.cancelledBookings === 1 ? '' : 's'} cancelled,
              {' '}
              {closeResult.summary.refundedCount} fully refunded.
            </p>
            {closeResult.summary.failedRefunds > 0 && (
              <p className="mt-1 font-medium text-red-700">
                {closeResult.summary.failedRefunds} booking{closeResult.summary.failedRefunds === 1 ? '' : 's'} could not be
                cancelled or refunded and still need attention (booking ids: {closeResult.summary.failedBookingIds.join(', ')}).
              </p>
            )}
          </div>
          <Button type="button" variant="secondary" onClick={() => setCloseResult(null)}>
            Dismiss
          </Button>
        </div>
      )}<div className="rounded-2xl border border-slate-200 bg-white shadow-soft">
          <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-4">
            <div className="min-w-48 flex-1">
              <Input
                  id="destination-search"
                  type="search"
                  placeholder="Search name, district or category"
                  aria-label="Search destinations"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <Select
                id="destination-status-filter"
                aria-label="Filter by status"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as DestinationStatus | '')}
            >
              <option value="">All statuses</option>
              {STATUS_FILTER_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
              ))}
            </Select>
            <span className="text-sm text-slate-500">
            {filtered.length} destination{filtered.length === 1 ? '' : 's'}
          </span>
          </div>

          {loading && <p className="p-6 text-slate-600">Loading...</p>}

          {!loading && !error && destinations.length === 0 && (
              <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-cobalt-50 text-cobalt-600">
              <svg
                  width="32"
                  height="32"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  aria-hidden="true"
              >
                <path d="M12 21s7-6.2 7-11.5A7 7 0 005 9.5C5 14.8 12 21 12 21z" strokeLinejoin="round" />
                <circle cx="12" cy="9.5" r="2.5" />
              </svg>
            </span>
                <p className="text-lg font-semibold text-slate-900">No destinations yet</p>
                <p className="max-w-sm text-sm text-slate-600">
                  Destinations are the places tourists browse and attach packages and stays to. Start with your first one.
                </p>
                <Button onClick={() => setCreating(true)}>Create the first destination</Button>
              </div>
          )}

          {!loading && !error && destinations.length > 0 && filtered.length === 0 && (
              <p className="px-6 py-10 text-center text-slate-600">No destinations match your search or filter.</p>
          )}

          {!loading && !error && filtered.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[60rem] divide-y divide-slate-200 text-sm">
                  <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3">Province / District</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Packages</th>
                    <th className="px-4 py-3 text-right">Hotels</th>
                    <th className="min-w-56 px-4 py-3 text-right">Actions</th>
                  </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                  {filtered.map((destination) => {
                    const busy = busyId === destination.id
                    if (editingId === destination.id) {
                      return (
                          <tr key={destination.id}>
                            <td colSpan={6} className="bg-slate-50 p-4">
                              <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-soft">
                                <h2 className="text-lg font-semibold text-slate-900">Edit {destination.name}</h2>
                                <p className="mb-5 mt-1 text-sm text-slate-600">Update the details shown to tourists.</p>
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
                              </div>
                            </td>
                          </tr>
                      )
                    }

                    const status = destination.status
                    const menuItems: MenuItem[] = []
                    if (status === 'DRAFT') {
                      menuItems.push({
                        label: 'Submit for review',
                        onClick: () => void runAction(destination.id, submitDestinationForReview),
                      })
                    }
                    if (status === 'PUBLISHED' || status === 'TEMPORARILY_CLOSED') {
                      menuItems.push({
                        label: status === 'PUBLISHED' ? 'Close temporarily' : 'Update closure',
                        onClick: () => startClosing(destination.id),
                      })
                      menuItems.push({
                        label: 'Deactivate',
                        danger: true,
                        onClick: () => void runAction(destination.id, deactivateDestination),
                      })
                    }
                  if (status !== 'ARCHIVED') {
                    menuItems.push({
                      label: 'Archive',
                      danger: true,
                      onClick: () => confirmArchive(destination.id),
                    })
                  }

                    return [
                      <tr key={destination.id} className="text-slate-700 hover:bg-slate-50">
                        <td className="px-4 py-4">
                          <div className="flex items-center gap-3">
                            <Thumbnail url={destination.imageUrls?.[0]} name={destination.name} />
                            <div>
                              <p className="font-medium text-slate-900">{destination.name}</p>
                              <p className="text-xs text-slate-500">{destination.category}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-4">
                          {formatProvince(destination.province)} / {destination.district}
                        </td>
                        <td className="px-4 py-4">
                          <DestinationStatusBadge status={status} />
                          {status === 'TEMPORARILY_CLOSED' && destination.closureReason && (
                              <p className="mt-1 max-w-xs text-xs text-slate-500">
                                {destination.closureReason}
                                {closureRange(destination)}
                              </p>
                          )}
                        </td>
                        <td className="px-4 py-4 text-right">{destination.activePackageCount ?? '—'}</td>
                        <td className="px-4 py-4 text-right">{destination.activeAccommodationCount ?? '—'}</td>
                        <td className="px-4 py-4">
                          <div className="flex flex-wrapitems-center justify-end gap-2">
                            <Button variant="secondary" disabled={busy} onClick={() => setEditingId(destination.id)}>
                              Edit
                            </Button>
                            {(status === 'DRAFT' || status === 'PENDING_REVIEW') && (
                                <Button disabled={busy} onClick={() => runAction(destination.id, publishDestination)}>
                                  Publish
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
                            {(status === 'INACTIVE' || status === 'ARCHIVED') && (
                                <Button
                                    variant="secondary"
                                    disabled={busy}
                                    onClick={() => runAction(destination.id, reactivateDestination)}
                                >
                                  {status === 'ARCHIVED' ? 'Restore' : 'Reactivate'}
                                </Button>
                            )}
                            
                            <RowMenu items={menuItems} disabled={busy} />
                          </div>
                        </td>
                      </tr>,
                      closingId === destination.id && (
                          <tr key={`${destination.id}-close`}>
                            <td colSpan={6} className="bg-orange-50 px-4 py-4">
                              <form
                                  onSubmit={(e) => handleClose(e, destination.id)}
                                  className="flex flex-wrap items-end gap-3"
                              >
                                <div className="w-80 max-w-full">
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
                                    id={`closeFrom-${destination.id}`}
                              label="From (optional)"
                              type="date"
                              min={todayIso()}
                              value={closeFrom}
                              onChange={(e) => setCloseFrom(e.target.value)}
                            />
                            <Inputid={`closeUntil-${destination.id}`}
                                    label="Until (optional)"
                                    type="date"min={closeFrom || todayIso()}
                                    value={closeUntil}
                                    onChange={(e) => setCloseUntil(e.target.value)}
                                />
                                <Button type="submit" disabled={busy}>
                                  Mark as closed
                                </Button>
                                <Button type="button" variant="secondary" onClick={() => setClosingId(null)}>
                                  Cancel
                                </Button>
                              </form><p className="mt-2 text-xs text-slate-600">
                            Leave empty to close from today / until reopened manually
                          </p>
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
      </div>
  )
}
