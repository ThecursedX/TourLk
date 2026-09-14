import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getVehicleById } from '../../api/vehicleApi'
import { useAuthStore } from '../../auth/authStore'
import HireVehicleForm from '../../components/vehicles/HireVehicleForm'
import Card from '../../components/ui/Card'
import VehicleStatusBadge from '../../components/vehicles/VehicleStatusBadge'
import ReviewList from '../../components/reviews/ReviewList'
import type { VehicleResponseDto } from '../../types/vehicle'

export default function VehicleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [vehicle, setVehicle] = useState<VehicleResponseDto | null>(null)
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

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!vehicle) return null

  const canHire = vehicle.status === 'ACTIVE' && isAuthenticated && user?.role === 'TOURIST'

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
        <p className="text-xs text-slate-500">Operated by {vehicle.driverName}</p>
      </Card>

      {vehicle.status === 'ACTIVE' && (
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
          {canHire && <HireVehicleForm vehicleId={vehicle.id} pricePerDay={vehicle.pricePerDay} />}
        </Card>
      )}

      <ReviewList reviewableType="VEHICLE" reviewableId={vehicle.id} />
    </div>
  )
}
