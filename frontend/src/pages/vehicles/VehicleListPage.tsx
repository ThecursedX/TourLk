import { useEffect, useState, type FormEvent } from 'react'
import { browseVehicles } from '../../api/vehicleApi'
import VehicleCard from '../../components/vehicles/VehicleCard'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { VEHICLE_TYPES, type VehicleResponseDto, type VehicleType } from '../../types/vehicle'

export default function VehicleListPage() {
  const [vehicles, setVehicles] = useState<VehicleResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [vehicleType, setVehicleType] = useState<VehicleType | ''>('')
  const [minSeatingCapacity, setMinSeatingCapacity] = useState('')

  const load = (type?: VehicleType, capacity?: number) => {
    setLoading(true)
    setError(null)
    browseVehicles(type, capacity)
      .then(setVehicles)
      .catch(() => setError('Could not load vehicles. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load(vehicleType || undefined, minSeatingCapacity ? Number(minSeatingCapacity) : undefined)
  }

  const handleReset = () => {
    setVehicleType('')
    setMinSeatingCapacity('')
    load()
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Transport</h1>
        <p className="mt-1 text-slate-600">Browse vehicles available to hire.</p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3">
        <div className="w-48">
          <Select
            id="vehicle-type-filter"
            label="Vehicle type"
            value={vehicleType}
            onChange={(e) => setVehicleType(e.target.value as VehicleType | '')}
          >
            <option value="">Any type</option>
            {VEHICLE_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-40">
          <Input
            id="min-capacity-filter"
            label="Min seats"
            type="number"
            min={1}
            value={minSeatingCapacity}
            onChange={(e) => setMinSeatingCapacity(e.target.value)}
          />
        </div>
        <Button type="submit">Search</Button>
        <Button type="button" variant="secondary" onClick={handleReset}>
          Reset
        </Button>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && vehicles.length === 0 && (
        <p className="text-slate-600">No vehicles match your search.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {vehicles.map((vehicle) => (
          <VehicleCard key={vehicle.id} vehicle={vehicle} />
        ))}
      </div>
    </div>
  )
}
