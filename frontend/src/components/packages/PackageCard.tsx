import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { TourPackageResponseDto } from '../../types/tourPackage'
import Card from '../ui/Card'
import StatusBadge from './StatusBadge'

interface PackageCardProps {
  tourPackage: TourPackageResponseDto
  footer?: ReactNode
}

export default function PackageCard({ tourPackage, footer }: PackageCardProps) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{tourPackage.title}</h3>
        <StatusBadge status={tourPackage.status} />
      </div>
      <p className="text-sm text-slate-600">{tourPackage.destination.name}</p>
      <p className="line-clamp-2 text-sm text-slate-500">{tourPackage.description}</p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>{tourPackage.durationDays} days</span>
        <span className="font-semibold text-slate-900">
          {tourPackage.price.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
        </span>
      </div>
      <div className="flex items-center justify-between gap-2 pt-1">
        <Link
          to={`/packages/${tourPackage.id}`}
          className="text-sm font-medium text-blue-600 hover:underline"
        >
          View details
        </Link>
        {footer}
      </div>
    </Card>
  )
}
