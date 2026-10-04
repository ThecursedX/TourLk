import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  archiveVehicle,
  deactivateVehicle,
  getMyVehicles,
  reactivateVehicle,
  startVehicleMaintenance,
  submitVehicleForVerification,
} from '../../api/vehicleApi'
import VehicleCard from '../../components/vehicles/VehicleCard'
import Button from '../../components/ui/Button'
import type { VehicleResponseDto } from '../../types/vehicle'

export default function MyVehiclesPage() {
  const [vehicles, setVehicles] = useState<VehicleResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyVehicles()
      .then(setVehicles)
      .catch(() => setError('Could not load your vehicles. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const runAction = async (id: number, action: (id: number) => Promise<VehicleResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setVehicles((prev) => prev.map((v) => (v.id === id ? updated : v)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">My Vehicles</h1>
          <p className="mt-1 text-slate-600">Manage the vehicles you've registered.</p>
        </div>
        <Link to="/vehicles/new">
          <Button>Register Vehicle</Button>
        </Link>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && vehicles.length === 0 && (
        <p className="text-slate-600">You haven't registered any vehicles yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {vehicles.map((vehicle) => {
          const disabled = busyId === vehicle.id
          return (
            <VehicleCard
              key={vehicle.id}
              vehicle={vehicle}
              footer={
                <div className="flex flex-wrap gap-2">
                  {vehicle.status !== 'ARCHIVED' && (
                    <Link to={`/vehicles/${vehicle.id}/edit`}>
                      <Button variant="secondary" disabled={disabled}>
                        Edit
                      </Button>
                    </Link>
                  )}
                  {vehicle.status === 'DRAFT' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(vehicle.id, submitVehicleForVerification)}
                    >
                      Submit
                    </Button>
                  )}
                  {(vehicle.status === 'AVAILABLE' || vehicle.status === 'BOOKED') && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(vehicle.id, startVehicleMaintenance)}
                    >
                      Start maintenance
                    </Button>
                  )}
                  {(vehicle.status === 'AVAILABLE' ||
                    vehicle.status === 'BOOKED' ||
                    vehicle.status === 'UNDER_MAINTENANCE') && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(vehicle.id, deactivateVehicle)}
                    >
                      Take out of service
                    </Button>
                  )}
                  {(vehicle.status === 'OUT_OF_SERVICE' || vehicle.status === 'UNDER_MAINTENANCE') && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(vehicle.id, reactivateVehicle)}
                    >
                      Reactivate
                    </Button>
                  )}
                  {/* Out-of-service vehicles are never deleted; reactivate instead. */}
                  {vehicle.status !== 'ARCHIVED' && vehicle.status !== 'OUT_OF_SERVICE' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(vehicle.id, archiveVehicle)}
                    >
                      Archive
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
