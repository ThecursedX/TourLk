import { useEffect, useState } from 'react'
import { getRatingSummary, getReviewsFor } from '../../api/reviewApi'
import type { RatingSummaryDto, ReviewResponseDto, ReviewableType } from '../../types/review'
import ReviewCard from './ReviewCard'
import StarRating from './StarRating'

interface ReviewListProps {
  reviewableType: ReviewableType
  reviewableId: number
}

export default function ReviewList({ reviewableType, reviewableId }: ReviewListProps) {
  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [summary, setSummary] = useState<RatingSummaryDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([getReviewsFor(reviewableType, reviewableId), getRatingSummary(reviewableType, reviewableId)])
      .then(([reviewsData, summaryData]) => {
        setReviews(reviewsData)
        setSummary(summaryData)
      })
      .catch(() => setError('Could not load reviews. Please try again later.'))
      .finally(() => setLoading(false))
  }, [reviewableType, reviewableId])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Reviews</h2>
        {summary && summary.totalReviews > 0 && (
          <div className="flex items-center gap-2">
            <StarRating value={Math.round(summary.averageRating)} size="sm" />
            <span className="text-sm text-slate-600">
              {summary.averageRating.toFixed(1)} ({summary.totalReviews} review
              {summary.totalReviews === 1 ? '' : 's'})
            </span>
          </div>
        )}
      </div>

      {loading && <p className="text-slate-600">Loading reviews...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && reviews.length === 0 && <p className="text-slate-600">No reviews yet.</p>}

      <div className="flex flex-col gap-3">
        {reviews.map((review) => (
          <ReviewCard key={review.id} review={review} />
        ))}
      </div>
    </div>
  )
}
