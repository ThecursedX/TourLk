import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getVehicleById, updateVehicle } from '../../api/vehicleApi'
import VehicleForm from '../../components/vehicles/VehicleForm'
import Card from '../../components/ui/Card'
import type { VehicleRequestDto, VehicleResponseDto } from '../../types/vehicle'

export default function EditVehiclePage() {
  const { id } = useParams<{ id: string }>()
  const vehicleId = Number(id)

  const [vehicle, setVehicle] = useState<VehicleResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getVehicleById(vehicleId)
      .then(setVehicle)
      .catch(() => setError('Could not load this vehicle. Please try again later.'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const handleSubmit = async (values: VehicleRequestDto) => {
    const updated = await updateVehicle(vehicleId, values)
    setVehicle(updated)
  }

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!vehicle) return null

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Edit Vehicle</h1>
        <VehicleForm
          initialValues={{
            vehicleType: vehicle.vehicleType,
            make: vehicle.make,
            model: vehicle.model,
            registrationNumber: vehicle.registrationNumber,
            seatingCapacity: vehicle.seatingCapacity,
            pricePerDay: vehicle.pricePerDay,
          }}
          onSubmit={handleSubmit}
          submitLabel="Save Changes"
          submittingLabel="Saving..."
        />
      </Card>
    </div>
  )
}
