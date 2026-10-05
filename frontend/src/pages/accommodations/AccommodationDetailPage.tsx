import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getAccommodationById } from '../../api/accommodationApi'
import { useAuthStore } from '../../auth/authStore'
import AccommodationStatusBadge from '../../components/accommodations/AccommodationStatusBadge'
import ReserveRoomForm from '../../components/accommodations/ReserveRoomForm'
import RoomCard from '../../components/accommodations/RoomCard'
import Button from '../../components/ui/Button'
import LocationMap from '../../components/maps/LocationMap'
import Card from '../../components/ui/Card'
import ImageGallery from '../../components/ui/ImageGallery'
import ReviewList from '../../components/reviews/ReviewList'
import { formatProvince } from '../../types/destination'
import type { AccommodationResponseDto } from '../../types/accommodation'

export default function AccommodationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [accommodation, setAccommodation] = useState<AccommodationResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reservingRoomId, setReservingRoomId] = useState<number | null>(null)

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getAccommodationById(Number(id))
      .then(setAccommodation)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 404) {
          setError('This accommodation could not be found.')
        } else {
          setError('Could not load this accommodation. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!accommodation) return null

  // FULLY_BOOKED only means tonight is full; later dates can still be reserved (the server checks
  // per-date availability). TEMPORARILY_UNAVAILABLE stays visible but is not bookable.
  const bookable = accommodation.status === 'ACTIVE' || accommodation.status === 'FULLY_BOOKED'
  const canReserve = bookable && isAuthenticated && user?.role === 'TOURIST'

  return (
    <div className="flex flex-col gap-4">
      <Link to="/accommodations" className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to stays
      </Link>
      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{accommodation.name}</h1>
          <AccommodationStatusBadge status={accommodation.status} />
        </div>
        <p className="text-slate-600">
          {accommodation.location.name}
          <span className="text-slate-400">
            {' '}
            · {accommodation.location.district}, {formatProvince(accommodation.location.province)}
          </span>
          {accommodation.starRating && (
            <span className="text-slate-400"> · {accommodation.starRating}★</span>
          )}
        </p>
        {accommodation.address && <p className="text-sm text-slate-600">{accommodation.address}</p>}
        <LocationMap name={accommodation.name} latitude={accommodation.latitude} longitude={accommodation.longitude} />
        <div className="mx-auto w-full max-w-2xl">
          <ImageGallery urls={accommodation.imageUrls ?? []} alt={accommodation.name} />
        </div>
        <p className="whitespace-pre-line text-slate-700">{accommodation.description}</p>
        {(accommodation.facilities?.length ?? 0) > 0 && (
          <div>
            <h2 className="mb-1 text-xs uppercase text-slate-500">Facilities</h2>
            <div className="flex flex-wrap gap-2">
              {accommodation.facilities.map((facility) => (
                <span key={facility} className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700">
                  {facility}
                </span>
              ))}
            </div>
          </div>
        )}
        {accommodation.policies && (
          <div>
            <h2 className="mb-1 text-xs uppercase text-slate-500">Policies</h2>
            <p className="whitespace-pre-line text-sm text-slate-700">{accommodation.policies}</p>
          </div>
        )}
        <p className="text-xs text-slate-500">Listed by {accommodation.ownerName}</p>
      </Card>

      {accommodation.status === 'TEMPORARILY_UNAVAILABLE' && (
        <Card>
          <p className="text-slate-700">
            This property is temporarily unavailable and cannot be booked right now. Please check back later.
          </p>
        </Card>
      )}
      {accommodation.status === 'FULLY_BOOKED' && (
        <Card>
          <p className="text-slate-700">
            This property is fully booked tonight. You can still reserve for later dates if rooms are free.
          </p>
        </Card>
      )}

      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Room types</h2>
        {accommodation.rooms.length === 0 && (
          <p className="text-slate-600">This property hasn't listed any room types yet.</p>
        )}
        <div className="flex flex-col gap-4">
          {accommodation.rooms.map((room) => (
            <RoomCard
              key={room.id}
              room={room}
              footer={
                canReserve ? (
                  reservingRoomId === room.id ? (
                    <ReserveRoomForm roomId={room.id} onClose={() => setReservingRoomId(null)} />
                  ) : (
                    <Button onClick={() => setReservingRoomId(room.id)}>Reserve</Button>
                  )
                ) : !isAuthenticated ? (
                  <p className="text-sm text-slate-600">
                    <Link to="/login" className="font-medium text-blue-600 hover:underline">
                      Log in
                    </Link>{' '}
                    to reserve this room.
                  </p>
                ) : undefined
              }
            />
          ))}
        </div>
      </div>

      <ReviewList reviewableType="ACCOMMODATION" reviewableId={accommodation.id} />
    </div>
  )
}
