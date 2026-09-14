import { useEffect, useState } from 'react'
import { getMyAccommodations } from '../../api/accommodationApi'
import {
  cancelReservation,
  completeReservation,
  confirmReservation,
  getReservationsByRoom,
} from '../../api/roomReservationApi'
import ReservationCard from '../../components/accommodations/ReservationCard'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import type { AccommodationResponseDto, RoomReservationResponseDto } from '../../types/accommodation'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED'])

export default function OwnerReservationsPage() {
  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [loadingProperties, setLoadingProperties] = useState(true)
  const [propertiesError, setPropertiesError] = useState<string | null>(null)

  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [reservations, setReservations] = useState<RoomReservationResponseDto[]>([])
  const [loadingReservations, setLoadingReservations] = useState(false)
  const [reservationsError, setReservationsError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    getMyAccommodations()
      .then(setAccommodations)
      .catch(() => setPropertiesError('Could not load your properties. Please try again later.'))
      .finally(() => setLoadingProperties(false))
  }, [])

  const loadReservations = (roomId: string) => {
    setSelectedRoomId(roomId)
    if (!roomId) {
      setReservations([])
      return
    }
    setLoadingReservations(true)
    setReservationsError(null)
    getReservationsByRoom(Number(roomId))
      .then(setReservations)
      .catch(() => setReservationsError('Could not load reservations for this room. Please try again later.'))
      .finally(() => setLoadingReservations(false))
  }

  const runAction = async (
    id: number,
    action: (id: number) => Promise<RoomReservationResponseDto>,
  ) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setReservations((prev) => prev.map((r) => (r.id === id ? updated : r)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  const rooms = accommodations.flatMap((a) =>
    a.rooms.map((room) => ({ room, accommodationName: a.name })),
  )

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Reservations</h1>
        <p className="mt-1 text-slate-600">Reservations for your properties, by room type.</p>
      </div>

      {loadingProperties && <p className="text-slate-600">Loading your properties...</p>}
      {propertiesError && <p className="text-red-600">{propertiesError}</p>}

      {!loadingProperties && !propertiesError && rooms.length === 0 && (
        <p className="text-slate-600">You don't have any room types listed yet.</p>
      )}

      {rooms.length > 0 && (
        <div className="w-72">
          <Select
            id="room-select"
            label="Room type"
            value={selectedRoomId}
            onChange={(e) => loadReservations(e.target.value)}
          >
            <option value="">Select a room type&hellip;</option>
            {rooms.map(({ room, accommodationName }) => (
              <option key={room.id} value={room.id}>
                {accommodationName} — {room.roomType}
              </option>
            ))}
          </Select>
        </div>
      )}

      {loadingReservations && <p className="text-slate-600">Loading reservations...</p>}
      {reservationsError && <p className="text-red-600">{reservationsError}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {selectedRoomId && !loadingReservations && !reservationsError && reservations.length === 0 && (
        <p className="text-slate-600">No reservations for this room type yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {reservations.map((reservation) => {
          const disabled = busyId === reservation.id
          return (
            <ReservationCard
              key={reservation.id}
              reservation={reservation}
              footer={
                <div className="flex flex-wrap gap-2">
                  {reservation.status === 'PENDING' && (
                    <Button disabled={disabled} onClick={() => runAction(reservation.id, confirmReservation)}>
                      Confirm
                    </Button>
                  )}
                  {reservation.status === 'CONFIRMED' && (
                    <Button disabled={disabled} onClick={() => runAction(reservation.id, completeReservation)}>
                      Complete
                    </Button>
                  )}
                  {CANCELLABLE.has(reservation.status) && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(reservation.id, cancelReservation)}
                    >
                      Cancel
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
