import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { VehicleHireResponseDto } from '../../types/vehicle'
import Card from '../ui/Card'
import HireStatusBadge from './HireStatusBadge'

interface HireCardProps {
  hire: VehicleHireResponseDto
  footer?: ReactNode
}

export default function HireCard({ hire, footer }: HireCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">
          {hire.vehicle.make} {hire.vehicle.model}
        </h3>
        <HireStatusBadge status={hire.status} />
      </div>
      <p className="text-sm text-slate-600">
        {hire.vehicle.vehicleType} · {hire.vehicle.registrationNumber}
      </p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>
          {hire.startDate} &rarr; {hire.endDate}
        </span>
        <span className="font-semibold text-slate-900">
          {hire.totalPrice.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
        </span>
      </div>
      <p className="text-sm text-slate-500">Pickup: {hire.pickupLocation}</p>
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link to={`/vehicles/${hire.vehicle.id}`} className="text-sm font-medium text-blue-600 hover:underline">
          View vehicle
        </Link>
        {footer}
      </div>
    </Card>
  )
}
