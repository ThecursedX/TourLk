import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getBookedDates, getVehicleById } from '../../api/vehicleApi'
import { useAuthStore } from '../../auth/authStore'
import HireVehicleForm from '../../components/vehicles/HireVehicleForm'
import Card from '../../components/ui/Card'
import VehicleStatusBadge from '../../components/vehicles/VehicleStatusBadge'
import ReviewList from '../../components/reviews/ReviewList'
import ImageGallery from '../../components/ui/ImageGallery'
import BookedDatesList from '../../components/vehicles/BookedDatesList'
import { HIREABLE_VEHICLE_STATUSES, type BookedDateRange, type VehicleResponseDto } from '../../types/vehicle'

export default function VehicleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [vehicle, setVehicle] = useState<VehicleResponseDto | null>(null)
  const [bookedRanges, setBookedRanges] = useState<BookedDateRange[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getVehicleById(Number(id))
      .then(setVehicle)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 404) {
          setError('This vehicle could not be found.')
        } else {
          setError('Could not load this vehicle. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    getBookedDates(Number(id))
      .then(setBookedRanges)
      .catch(() => setBookedRanges([]))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!vehicle) return null

  const hireable = HIREABLE_VEHICLE_STATUSES.includes(vehicle.status)
  const canHire = hireable && isAuthenticated && user?.role === 'TOURIST'
  // Compliance dates are operational details for the owner and admins, not for browsing tourists.
  const canSeeCompliance = user?.role === 'ADMIN' || (user?.userId !== undefined && user.userId === vehicle.driverId)

  return (
    <div className="flex flex-col gap-4">
      <Link to="/vehicles" className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to transport
      </Link>
      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">
            {vehicle.make} {vehicle.model}
          </h1>
          <VehicleStatusBadge status={vehicle.status} />
        </div>
        <div className="mx-auto w-full max-w-2xl">
          <ImageGallery urls={vehicle.imageUrls ?? []} alt={`${vehicle.make} ${vehicle.model}`} />
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <dt className="text-xs uppercase text-slate-500">Type</dt>
            <dd className="text-slate-900">{vehicle.vehicleType}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Registration</dt>
            <dd className="text-slate-900">{vehicle.registrationNumber}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Seats</dt>
            <dd className="text-slate-900">{vehicle.seatingCapacity}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Price per day</dt>
            <dd className="text-slate-900">
              {vehicle.pricePerDay.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
            </dd>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <dt className="text-xs uppercase text-slate-500">Air conditioning</dt>
            <dd className="text-slate-900">{vehicle.airConditioned ? 'Yes' : 'No'}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Driver</dt>
            <dd className="text-slate-900">{vehicle.driverName}</dd>
          </div>
          {vehicle.driverPhone && (
            <div>
              <dt className="text-xs uppercase text-slate-500">Driver phone</dt>
              <dd className="text-slate-900">{vehicle.driverPhone}</dd>
            </div>
          )}
        </div>
        {(vehicle.facilities?.length ?? 0) > 0 && (
          <div>
            <dt className="mb-1 text-xs uppercase text-slate-500">Facilities</dt>
            <dd className="flex flex-wrap gap-2">
              {vehicle.facilities.map((facility) => (
                <span key={facility} className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700">
                  {facility}
                </span>
              ))}
            </dd>
          </div>
        )}
        {canSeeCompliance && (
          <div className="grid grid-cols-1 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-3">
            <div>
              <dt className="text-xs uppercase text-slate-500">Insurance expiry</dt>
              <dd className="text-slate-900">{vehicle.insuranceExpiry ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase text-slate-500">Last maintenance</dt>
              <dd className="text-slate-900">{vehicle.lastMaintenanceDate ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase text-slate-500">Next maintenance</dt>
              <dd className="text-slate-900">{vehicle.nextMaintenanceDate ?? '—'}</dd>
            </div>
          </div>
        )}
        <p className="text-xs text-slate-500">Operated by {vehicle.driverName}</p>
      </Card>

      {vehicle.status === 'UNDER_MAINTENANCE' && (
        <Card>
          <p className="text-slate-600">This vehicle is under maintenance and cannot be hired right now.</p>
        </Card>
      )}

      {hireable && (
        <Card className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold text-slate-900">Hire this vehicle</h2>
          {!isAuthenticated && (
            <p className="text-slate-600">
              Please{' '}
              <Link to="/login" className="font-medium text-blue-600 hover:underline">
                log in
              </Link>{' '}
              to hire this vehicle.
            </p>
          )}
          {!canHire && <BookedDatesList ranges={bookedRanges} />}
          {canHire && (
            <HireVehicleForm vehicleId={vehicle.id} pricePerDay={vehicle.pricePerDay} bookedRanges={bookedRanges} />
          )}
        </Card>
      )}

      <ReviewList reviewableType="VEHICLE" reviewableId={vehicle.id} />
    </div>
  )
}
