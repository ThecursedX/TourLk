import { useEffect, useState } from 'react'
import { approveVehicle, getPendingApprovalVehicles, rejectVehicle } from '../../api/vehicleApi'
import VehicleCard from '../../components/vehicles/VehicleCard'
import Button from '../../components/ui/Button'
import type { VehicleResponseDto } from '../../types/vehicle'

export default function AdminVehicleApprovalsPage() {
  const [vehicles, setVehicles] = useState<VehicleResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getPendingApprovalVehicles()
      .then(setVehicles)
      .catch(() => setError('Could not load pending vehicles. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleDecision = async (id: number, decision: (id: number) => Promise<VehicleResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      await decision(id)
      setVehicles((prev) => prev.filter((v) => v.id !== id))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Vehicle Approvals</h1>
        <p className="mt-1 text-slate-600">Vehicles awaiting approval.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && vehicles.length === 0 && (
        <p className="text-slate-600">No vehicles are waiting for approval.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {vehicles.map((vehicle) => {
          const disabled = busyId === vehicle.id
          return (
            <VehicleCard
              key={vehicle.id}
              vehicle={vehicle}
              footer={
                <div className="flex gap-2">
                  <Button disabled={disabled} onClick={() => handleDecision(vehicle.id, approveVehicle)}>
                    Approve
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={disabled}
                    onClick={() => handleDecision(vehicle.id, rejectVehicle)}
                  >
                    Reject
                  </Button>
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
