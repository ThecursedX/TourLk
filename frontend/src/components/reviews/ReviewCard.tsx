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
    <Card className="flex flex-col gap-2" attention={review.flagged}>
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-slate-900">{review.reviewerName}</span>
        <StarRating value={review.rating} size="sm" />
      </div>
      {review.comment && <p className="text-sm text-slate-700">{review.comment}</p>}
      <p className="text-xs text-slate-500">{new Date(review.createdAt).toLocaleDateString()}</p>
      {review.flagged && (
        <span className="w-fit rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
          Flagged for moderation
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
