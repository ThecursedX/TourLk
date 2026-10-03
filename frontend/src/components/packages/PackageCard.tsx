import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { TourPackageResponseDto } from '../../types/tourPackage'
import Card from '../ui/Card'
import DestinationClosureBanner from '../destinations/DestinationClosureBanner'
import StatusBadge from './StatusBadge'
import StarRating from '../reviews/StarRating'

interface PackageCardProps {
  tourPackage: TourPackageResponseDto
  footer?: ReactNode
}

export default function PackageCard({ tourPackage, footer }: PackageCardProps) {
  const coverImage = tourPackage.imageUrls[0]

  return (
    <Card className="flex flex-col gap-3" attention={tourPackage.status === 'PENDING_APPROVAL'}>
      {coverImage && (
        <img src={coverImage} alt={tourPackage.title} className="h-40 w-full rounded-xl object-cover" />
      )}
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{tourPackage.title}</h3>
        <StatusBadge status={tourPackage.status} />
      </div>
      <p className="text-sm text-slate-600">{tourPackage.destination.name}</p>
      <DestinationClosureBanner destination={tourPackage.destination} compact />
      {tourPackage.reviewCount > 0 ? (
        <div className="flex items-center gap-1.5 text-sm text-slate-600">
          <StarRating value={Math.round(tourPackage.averageRating)} size="sm" />
          <span className="font-medium text-slate-800">{tourPackage.averageRating.toFixed(1)}</span>
          <span>
            ({tourPackage.reviewCount} review{tourPackage.reviewCount === 1 ? '' : 's'})
          </span>
        </div>
      ) : (
        <p className="text-sm text-slate-400">No reviews yet</p>
      )}
      <p className="line-clamp-2 text-sm text-slate-500">{tourPackage.description}</p>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>{tourPackage.durationDays} days</span>
        <span className="font-semibold text-slate-900">
          {tourPackage.price.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
        </span>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
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
