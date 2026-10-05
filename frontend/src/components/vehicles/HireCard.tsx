import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { VehicleHireResponseDto } from '../../types/vehicle'
import Card from '../ui/Card'
import CardImage from '../ui/CardImage'
import HireStatusBadge from './HireStatusBadge'

interface HireCardProps {
  hire: VehicleHireResponseDto
  footer?: ReactNode
}

export default function HireCard({ hire, footer }: HireCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start gap-3">
        <CardImage
          urls={hire.vehicle.coverImageUrl ? [hire.vehicle.coverImageUrl] : []}
          alt={`${hire.vehicle.make} ${hire.vehicle.model}`}
          className="!aspect-auto h-20 w-28 shrink-0"
        />
        <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-2">
          <h3 className="min-w-0 break-words text-lg font-semibold text-slate-900">
            {hire.vehicle.make} {hire.vehicle.model}
          </h3>
          <HireStatusBadge status={hire.status} />
        </div>
      </div>
      {hire.bookingId && (
        <Link
          to={`/bookings/${hire.bookingId}`}
          className="self-start rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-medium text-indigo-800 hover:underline"
        >
          Part of package: {hire.packageTitle}
        </Link>
      )}
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
      <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <Link to={`/vehicles/${hire.vehicle.id}`} className="text-sm font-medium text-blue-600 hover:underline">
          View vehicle
        </Link>
        {footer}
      </div>
    </Card>
  )
}
