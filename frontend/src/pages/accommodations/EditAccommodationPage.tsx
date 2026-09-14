import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  addRoom,
  getAccommodationById,
  removeRoom,
  updateAccommodation,
  updateRoom,
} from '../../api/accommodationApi'
import AccommodationForm from '../../components/accommodations/AccommodationForm'
import RoomCard from '../../components/accommodations/RoomCard'
import RoomForm from '../../components/accommodations/RoomForm'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { AccommodationResponseDto, AccommodationRequestDto, RoomRequestDto } from '../../types/accommodation'

export default function EditAccommodationPage() {
  const { id } = useParams<{ id: string }>()
  const accommodationId = Number(id)

  const [accommodation, setAccommodation] = useState<AccommodationResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [roomActionError, setRoomActionError] = useState<string | null>(null)
  const [busyRoomId, setBusyRoomId] = useState<number | null>(null)
  const [editingRoomId, setEditingRoomId] = useState<number | null>(null)
  const [addingRoom, setAddingRoom] = useState(false)

  const load = () => {
    if (!id) return
    setLoading(true)
    setError(null)
    getAccommodationById(accommodationId)
      .then(setAccommodation)
      .catch(() => setError('Could not load this property. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const handlePropertySubmit = async (values: AccommodationRequestDto) => {
    const updated = await updateAccommodation(accommodationId, values)
    setAccommodation(updated)
  }

  const handleAddRoom = async (values: RoomRequestDto) => {
    await addRoom(accommodationId, values)
    setAddingRoom(false)
    load()
  }

  const handleUpdateRoom = async (roomId: number, values: RoomRequestDto) => {
    await updateRoom(roomId, values)
    setEditingRoomId(null)
    load()
  }

  const handleRemoveRoom = async (roomId: number) => {
    setRoomActionError(null)
    setBusyRoomId(roomId)
    try {
      await removeRoom(roomId)
      load()
    } catch {
      setRoomActionError('That room type could not be removed. Please try again.')
    } finally {
      setBusyRoomId(null)
    }
  }

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!accommodation) return null

  return (
    <div className="flex flex-col gap-6">
      <Card className="w-full">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Edit Property</h1>
        <AccommodationForm
          initialValues={{
            name: accommodation.name,
            description: accommodation.description,
            locationId: accommodation.location.id,
            starRating: accommodation.starRating ?? undefined,
          }}
          currentLocation={accommodation.location}
          onSubmit={handlePropertySubmit}
          submitLabel="Save Changes"
          submittingLabel="Saving..."
        />
      </Card>

      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Room types</h2>
          {!addingRoom && <Button onClick={() => setAddingRoom(true)}>Add Room Type</Button>}
        </div>

        {roomActionError && <p className="mb-3 text-red-600">{roomActionError}</p>}

        {addingRoom && (
          <div className="mb-4">
            <RoomForm
              onSubmit={handleAddRoom}
              onCancel={() => setAddingRoom(false)}
              submitLabel="Add Room Type"
            />
          </div>
        )}

        <div className="flex flex-col gap-4">
          {accommodation.rooms.map((room) =>
            editingRoomId === room.id ? (
              <RoomForm
                key={room.id}
                initialValues={{
                  roomType: room.roomType,
                  pricePerNight: room.pricePerNight,
                  totalRooms: room.totalRooms,
                  maxOccupancy: room.maxOccupancy,
                }}
                onSubmit={(values) => handleUpdateRoom(room.id, values)}
                onCancel={() => setEditingRoomId(null)}
                submitLabel="Save Room"
              />
            ) : (
              <RoomCard
                key={room.id}
                room={room}
                footer={
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      disabled={busyRoomId === room.id}
                      onClick={() => setEditingRoomId(room.id)}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="secondary"
                      disabled={busyRoomId === room.id}
                      onClick={() => handleRemoveRoom(room.id)}
                    >
                      Remove
                    </Button>
                  </div>
                }
              />
            ),
          )}
        </div>
      </div>
    </div>
  )
}
