import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { confirmBooking, getBookingsByPackage, getMyPackageBookings, rejectBooking } from '../../api/bookingApi'
import BookingCard from '../../components/bookings/BookingCard'
import RejectReasonForm from '../../components/packages/RejectReasonForm'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import type { ErrorResponse } from '../../types/auth'
import type { BookingResponseDto, BookingStatus } from '../../types/booking'

const STATUS_FILTERS: { value: BookingStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'COMPLETED', label: 'Completed' },
]

function errorMessage(err: unknown, fallback: string) {
  return isAxiosError<ErrorResponse>(err) && err.response?.data?.message ? err.response.data.message : fallback
}

/**
 * GUIDE-facing: every booking on your own packages (shown on open), optionally narrowed to one package by ID
 * and/or a status, with Confirm / Reject on PENDING bookings.
 */
export default function MyPackageBookingsPage() {
  const [packageId, setPackageId] = useState('')
  // The package the current results were loaded for; null means "all bookings".
  const [shownPackageId, setShownPackageId] = useState<number | null>(null)
  const [bookings, setBookings] = useState<BookingResponseDto[]>([])
  const [statusFilter, setStatusFilter] = useState<BookingStatus | ''>('')
  const [loading, setLoading] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [rejectingId, setRejectingId] = useState<number | null>(null)

  const loadAll = () => {
    setLoading(true)
    setError(null)
    setActionError(null)
    setRejectingId(null)
    getMyPackageBookings()
      .then((data) => {
        setBookings(data)
        setShownPackageId(null)
        setLoaded(true)
      })
      .catch((err) => setError(errorMessage(err, 'Could not load bookings. Please try again later.')))
      .finally(() => setLoading(false))
  }

  const loadForPackage = (id: number) => {
    setLoading(true)
    setError(null)
    setActionError(null)
    setRejectingId(null)
    getBookingsByPackage(id)
      .then((data) => {
        setBookings(data)
        setShownPackageId(id)
        setLoaded(true)
      })
      .catch((err) => setError(errorMessage(err, 'Could not load bookings for this package. Please try again later.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    if (!packageId) return
    loadForPackage(Number(packageId))
  }

  const handleShowAll = () => {
    setPackageId('')
    loadAll()
  }

  const replaceBooking = (updated: BookingResponseDto) =>
    setBookings((prev) => prev.map((b) => (b.id === updated.id ? updated : b)))

  const handleConfirm = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      replaceBooking(await confirmBooking(id))
    } catch (err) {
      setActionError(errorMessage(err, 'That booking could not be confirmed. Please try again.'))
    } finally {
      setBusyId(null)
    }
  }

  const handleReject = async (id: number, reason: string) => {
    setActionError(null)
    setBusyId(id)
    try {
      replaceBooking(await rejectBooking(id, reason))
      setRejectingId(null)
    } catch (err) {
      setActionError(errorMessage(err, 'That booking could not be rejected. Please try again.'))
    } finally {
      setBusyId(null)
    }
  }

  const visible = useMemo(
    () => (statusFilter ? bookings.filter((b) => b.status === statusFilter) : bookings),
    [bookings, statusFilter],
  )

  const emptyText =
    shownPackageId === null
      ? bookings.length === 0
        ? 'No bookings yet.'
        : 'No bookings match that status.'
      : bookings.length === 0
        ? 'No bookings found for that package.'
        : 'No bookings match that status.'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Package Bookings</h1>
        <p className="mt-1 text-slate-600">
          Bookings on your tour packages. Search by package ID to narrow them down.
        </p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3">
        <div className="w-40">
          <Input
            id="package-id"
            label="Package ID"
            type="number"
            min={1}
            value={packageId}
            onChange={(e) => setPackageId(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={loading || !packageId}>
          Load Bookings
        </Button>
        <Button type="button" variant="secondary" disabled={loading} onClick={handleShowAll}>
          All Bookings
        </Button>
        <div className="w-44">
          <Select
            id="booking-status-filter"
            aria-label="Filter by status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as BookingStatus | '')}
          >
            {STATUS_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {loaded && !loading && !error && (
        <p className="text-sm font-medium text-slate-500">
          {shownPackageId === null ? 'Showing all bookings' : `Showing bookings for package #${shownPackageId}`}
        </p>
      )}

      {loaded && !loading && !error && visible.length === 0 && <p className="text-slate-600">{emptyText}</p>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((booking) => {
          const disabled = busyId === booking.id
          const isRejectingThis = rejectingId === booking.id
          return (
            <BookingCard
              key={booking.id}
              booking={booking}
              footer={
                booking.status === 'PENDING' ? (
                  isRejectingThis ? (
                    <RejectReasonForm
                      disabled={disabled}
                      onCancel={() => setRejectingId(null)}
                      onSubmit={(reason) => handleReject(booking.id, reason)}
                    />
                  ) : (
                    <div className="flex gap-2">
                      <Button disabled={disabled} onClick={() => handleConfirm(booking.id)}>
                        Confirm
                      </Button>
                      <Button variant="secondary" disabled={disabled} onClick={() => setRejectingId(booking.id)}>
                        Reject
                      </Button>
                    </div>
                  )
                ) : null
              }
            />
          )
        })}
      </div>
    </div>
  )
}
