import type { ReactNode } from 'react'
import type { ReviewResponseDto } from '../../types/review'
import Card from '../ui/Card'
import StarRating from './StarRating'

interface ReviewCardProps {
  review: ReviewResponseDto
  footer?: ReactNode
}

export default function ReviewCard({ review, footer }: ReviewCardProps) {
  return (
    <Card className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-slate-900">{review.reviewerName}</span>
        <StarRating value={review.rating} size="sm" />
      </div>
      {review.comment && <p className="text-sm text-slate-700">{review.comment}</p>}
      <p className="text-xs text-slate-500">{new Date(review.createdAt).toLocaleDateString()}</p>
      {footer}
    </Card>
  )
}
