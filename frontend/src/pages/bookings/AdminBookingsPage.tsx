import { useState, type FormEvent } from 'react'
import {
  approveReschedule,
  completeBooking,
  confirmBooking,
  getBookingsByPackage,
  rejectBooking,
  rejectReschedule,
} from '../../api/bookingApi'
import BookingCard from '../../components/bookings/BookingCard'
import CancelBookingPanel from '../../components/bookings/CancelBookingPanel'
import RejectReasonForm from '../../components/packages/RejectReasonForm'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import type { BookingResponseDto } from '../../types/booking'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED', 'RESCHEDULE_REQUESTED', 'RESCHEDULED'])
const COMPLETABLE = new Set(['CONFIRMED', 'RESCHEDULED'])

export default function AdminBookingsPage() {
  const [packageId, setPackageId] = useState('')
  const [bookings, setBookings] = useState<BookingResponseDto[]>([])
  const [loading, setLoading] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)
  const [rejectingId, setRejectingId] = useState<number | null>(null)

  const load = (id: string) => {
    if (!id) return
    setLoading(true)
    setError(null)
    getBookingsByPackage(Number(id))
      .then((data) => {
        setBookings(data)
        setLoaded(true)
      })
      .catch(() => setError('Could not load bookings for this package. Please try again later.'))
      .finally(() => setLoading(false))
  }

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load(packageId)
  }

  const runAction = async (id: number, action: (id: number) => Promise<BookingResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setBookings((prev) => prev.map((b) => (b.id === id ? updated : b)))
      setRejectingId(null)
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Manage Bookings</h1>
        <p className="mt-1 text-slate-600">Look up bookings for a tour package by its ID.</p>
      </div>

      <form onSubmit={handleSearch} className="flex items-end gap-3">
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
        <Button type="submit">Load Bookings</Button>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {loaded && !loading && !error && bookings.length === 0 && (
        <p className="text-slate-600">No bookings found for that package.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {bookings.map((booking) => {
          const disabled = busyId === booking.id
          const isCancellingThis = cancellingId === booking.id
          const isRejectingThis = rejectingId === booking.id
          return (
            <BookingCard
              key={booking.id}
              booking={booking}
              footer={
                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap gap-2">
                    {booking.status === 'PENDING' && !isRejectingThis && (
                      <Button disabled={disabled} onClick={() => runAction(booking.id, confirmBooking)}>
                        Confirm
                      </Button>
                    )}
                    {booking.status === 'PENDING' && !isRejectingThis && (
                      <Button
                        variant="secondary"
                        disabled={disabled}
                        onClick={() => setRejectingId(booking.id)}
                      >
                        Reject
                      </Button>
                    )}
                    {booking.status === 'RESCHEDULE_REQUESTED' && (
                      <>
                        <Button disabled={disabled} onClick={() => runAction(booking.id, approveReschedule)}>
                          Approve Reschedule
                        </Button>
                        <Button
                          variant="secondary"
                          disabled={disabled}
                          onClick={() => runAction(booking.id, rejectReschedule)}
                        >
                          Reject Reschedule
                        </Button>
                      </>
                    )}
                    {COMPLETABLE.has(booking.status) && (
                      <Button disabled={disabled} onClick={() => runAction(booking.id, completeBooking)}>
                        Complete
                      </Button>
                    )}
                    {CANCELLABLE.has(booking.status) && !isCancellingThis && (
                      <Button
                        variant="secondary"
                        disabled={disabled}
                        onClick={() => setCancellingId(booking.id)}
                      >
                        Cancel
                      </Button>
                    )}
                  </div>
                  {isRejectingThis && (
                    <RejectReasonForm
                      disabled={disabled}
                      onCancel={() => setRejectingId(null)}
                      onSubmit={(reason) => runAction(booking.id, (id) => rejectBooking(id, reason))}
                    />
                  )}
                  {isCancellingThis && (
                    <CancelBookingPanel
                      bookingId={booking.id}
                      onCancelled={(updated) => {
                        setBookings((prev) => prev.map((b) => (b.id === updated.id ? updated : b)))
                        setCancellingId(null)
                      }}
                      onClose={() => setCancellingId(null)}
                    />
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
