import { useEffect, useState } from 'react'
import { getMyVehicles } from '../../api/vehicleApi'
import { cancelHire, completeHire, confirmHire, getHiresByVehicle } from '../../api/vehicleHireApi'
import HireCard from '../../components/vehicles/HireCard'
import Button from '../../components/ui/Button'
import Select from '../../components/ui/Select'
import type { VehicleHireResponseDto, VehicleResponseDto } from '../../types/vehicle'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED'])

export default function OwnerHiresPage() {
  const [vehicles, setVehicles] = useState<VehicleResponseDto[]>([])
  const [loadingVehicles, setLoadingVehicles] = useState(true)
  const [vehiclesError, setVehiclesError] = useState<string | null>(null)

  const [selectedVehicleId, setSelectedVehicleId] = useState('')
  const [hires, setHires] = useState<VehicleHireResponseDto[]>([])
  const [loadingHires, setLoadingHires] = useState(false)
  const [hiresError, setHiresError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    getMyVehicles()
      .then(setVehicles)
      .catch(() => setVehiclesError('Could not load your vehicles. Please try again later.'))
      .finally(() => setLoadingVehicles(false))
  }, [])

  const loadHires = (vehicleId: string) => {
    setSelectedVehicleId(vehicleId)
    if (!vehicleId) {
      setHires([])
      return
    }
    setLoadingHires(true)
    setHiresError(null)
    getHiresByVehicle(Number(vehicleId))
      .then(setHires)
      .catch(() => setHiresError('Could not load hires for this vehicle. Please try again later.'))
      .finally(() => setLoadingHires(false))
  }

  const runAction = async (id: number, action: (id: number) => Promise<VehicleHireResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setHires((prev) => prev.map((h) => (h.id === id ? updated : h)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Hires</h1>
        <p className="mt-1 text-slate-600">Hires for your vehicles.</p>
      </div>

      {loadingVehicles && <p className="text-slate-600">Loading your vehicles...</p>}
      {vehiclesError && <p className="text-red-600">{vehiclesError}</p>}

      {!loadingVehicles && !vehiclesError && vehicles.length === 0 && (
        <p className="text-slate-600">You don't have any vehicles registered yet.</p>
      )}

      {vehicles.length > 0 && (
        <div className="w-72">
          <Select
            id="vehicle-select"
            label="Vehicle"
            value={selectedVehicleId}
            onChange={(e) => loadHires(e.target.value)}
          >
            <option value="">Select a vehicle&hellip;</option>
            {vehicles.map((vehicle) => (
              <option key={vehicle.id} value={vehicle.id}>
                {vehicle.make} {vehicle.model} — {vehicle.registrationNumber}
              </option>
            ))}
          </Select>
        </div>
      )}

      {loadingHires && <p className="text-slate-600">Loading hires...</p>}
      {hiresError && <p className="text-red-600">{hiresError}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {selectedVehicleId && !loadingHires && !hiresError && hires.length === 0 && (
        <p className="text-slate-600">No hires for this vehicle yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {hires.map((hire) => {
          const disabled = busyId === hire.id
          return (
            <HireCard
              key={hire.id}
              hire={hire}
              footer={
                <div className="flex flex-wrap gap-2">
                  {hire.status === 'PENDING' && (
                    <Button disabled={disabled} onClick={() => runAction(hire.id, confirmHire)}>
                      Confirm
                    </Button>
                  )}
                  {hire.status === 'CONFIRMED' && (
                    <Button disabled={disabled} onClick={() => runAction(hire.id, completeHire)}>
                      Complete
                    </Button>
                  )}
                  {CANCELLABLE.has(hire.status) && (
                    <Button variant="secondary" disabled={disabled} onClick={() => runAction(hire.id, cancelHire)}>
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
