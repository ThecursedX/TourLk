import type { ReactNode } from 'react'
import type { ReviewResponseDto, ReviewStatus } from '../../types/review'
import Card from '../ui/Card'
import StarRating from './StarRating'

interface ReviewCardProps {
  review: ReviewResponseDto
  footer?: ReactNode
}

const STATUS_BADGE: Partial<Record<ReviewStatus, string>> = {
  EDITED: 'bg-slate-100 text-slate-700',
  REPORTED: 'bg-amber-100 text-amber-800',
  HIDDEN: 'bg-red-50 text-red-700',
  DELETED: 'bg-red-50 text-red-700',
}

const STATUS_LABEL: Partial<Record<ReviewStatus, string>> = {
  EDITED: 'Edited',
  REPORTED: 'Reported for moderation',
  HIDDEN: 'Hidden by admin',
  DELETED: 'Removed by admin',
}

const NEEDS_ATTENTION: ReviewStatus[] = ['REPORTED', 'HIDDEN']

export default function ReviewCard({ review, footer }: ReviewCardProps) {
  const badgeLabel = STATUS_LABEL[review.status]

  return (
    <Card className="flex flex-col gap-2" attention={NEEDS_ATTENTION.includes(review.status)}>
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-slate-900">{review.reviewerName}</span>
        <StarRating value={review.rating} size="sm" />
      </div>
      {review.comment && <p className="text-sm text-slate-700">{review.comment}</p>}
      {review.imageUrls.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {review.imageUrls.map((url) => (
            <img key={url} src={url} alt="" className="h-16 w-16 rounded-lg object-cover" />
          ))}
        </div>
      )}
      <p className="text-xs text-slate-500">{new Date(review.createdAt).toLocaleDateString()}</p>
      {badgeLabel && (
        <span className={`w-fit rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[review.status]}`}>
          {badgeLabel}
        </span>
      )}
      {review.guideReply && (
        <div className="ml-4 rounded-md bg-slate-50 p-2 text-sm">
          <span className="font-medium text-slate-700">Guide&apos;s reply: </span>
          <span className="text-slate-600">{review.guideReply}</span>
        </div>
      )}
      {footer}
    </Card>
  )
}
