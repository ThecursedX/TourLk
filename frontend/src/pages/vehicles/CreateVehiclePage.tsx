import { useNavigate } from 'react-router-dom'
import { createVehicle } from '../../api/vehicleApi'
import VehicleForm from '../../components/vehicles/VehicleForm'
import Card from '../../components/ui/Card'
import type { VehicleRequestDto } from '../../types/vehicle'

export default function CreateVehiclePage() {
  const navigate = useNavigate()

  const handleSubmit = async (values: VehicleRequestDto) => {
    await createVehicle(values)
    navigate('/vehicles/mine')
  }

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-2 text-xl font-semibold text-slate-900">Register Vehicle</h1>
        <p className="mb-6 text-sm text-slate-600">
          Your vehicle will be listed once an admin approves it.
        </p>
        <VehicleForm onSubmit={handleSubmit} submitLabel="Register Vehicle" submittingLabel="Registering..." />
      </Card>
    </div>
  )
}
