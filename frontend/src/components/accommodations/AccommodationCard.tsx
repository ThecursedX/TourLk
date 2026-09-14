import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { AccommodationResponseDto } from '../../types/accommodation'
import Card from '../ui/Card'
import AccommodationStatusBadge from './AccommodationStatusBadge'

interface AccommodationCardProps {
  accommodation: AccommodationResponseDto
  footer?: ReactNode
}

export default function AccommodationCard({ accommodation, footer }: AccommodationCardProps) {
  const cheapest = accommodation.rooms.length
    ? Math.min(...accommodation.rooms.map((r) => r.pricePerNight))
    : null

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{accommodation.name}</h3>
        <AccommodationStatusBadge status={accommodation.status} />
      </div>
      <p className="text-sm text-slate-600">
        {accommodation.location.name}
        {accommodation.starRating && (
          <span className="text-slate-400"> · {accommodation.starRating}★</span>
        )}
      </p>
      <p className="line-clamp-2 text-sm text-slate-500">{accommodation.description}</p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>
          {accommodation.rooms.length} room type{accommodation.rooms.length === 1 ? '' : 's'}
        </span>
        {cheapest !== null && (
          <span className="font-semibold text-slate-900">
            From {cheapest.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
            <span className="font-normal text-slate-500"> / night</span>
          </span>
        )}
      </div>
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link
          to={`/accommodations/${accommodation.id}`}
          className="text-sm font-medium text-blue-600 hover:underline"
        >
          View details
        </Link>
        {footer}
      </div>
    </Card>
  )
}
