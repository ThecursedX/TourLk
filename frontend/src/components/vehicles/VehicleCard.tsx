import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { VehicleResponseDto } from '../../types/vehicle'
import Card from '../ui/Card'
import VehicleStatusBadge from './VehicleStatusBadge'

interface VehicleCardProps {
  vehicle: VehicleResponseDto
  footer?: ReactNode
}

export default function VehicleCard({ vehicle, footer }: VehicleCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">
          {vehicle.make} {vehicle.model}
        </h3>
        <VehicleStatusBadge status={vehicle.status} />
      </div>
      <p className="text-sm text-slate-600">
        {vehicle.vehicleType} · {vehicle.registrationNumber}
      </p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>Seats {vehicle.seatingCapacity}</span>
        <span className="font-semibold text-slate-900">
          {vehicle.pricePerDay.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
          <span className="font-normal text-slate-500"> / day</span>
        </span>
      </div>
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link to={`/vehicles/${vehicle.id}`} className="text-sm font-medium text-blue-600 hover:underline">
          View details
        </Link>
        {footer}
      </div>
    </Card>
  )
}
