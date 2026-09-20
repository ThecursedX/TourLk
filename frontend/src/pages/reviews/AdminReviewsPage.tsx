import { useEffect, useState } from 'react'
import {
  deleteReview,
  flagReview,
  getAllReviewsForAdmin,
  removeGuideReply,
  unflagReview,
} from '../../api/reviewApi'
import ReviewCard from '../../components/reviews/ReviewCard'
import Button from '../../components/ui/Button'
import type { ReviewResponseDto } from '../../types/review'

const TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Flagged', value: true },
]

export default function AdminReviewsPage() {
  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [flaggedOnly, setFlaggedOnly] = useState<boolean | undefined>(undefined)

  const load = (filter?: boolean) => {
    setLoading(true)
    setError(null)
    getAllReviewsForAdmin(filter)
      .then(setReviews)
      .catch(() => setError('Could not load reviews. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load(flaggedOnly)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flaggedOnly])

  const runAction = async (id: number, action: (id: number) => Promise<ReviewResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setReviews((prev) => prev.map((r) => (r.id === id ? updated : r)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  const handleRemove = async (id: number) => {
    if (!window.confirm('Permanently remove this review? This cannot be undone.')) return
    setActionError(null)
    setBusyId(id)
    try {
      await deleteReview(id)
      setReviews((prev) => prev.filter((r) => r.id !== id))
    } catch {
      setActionError('That review could not be removed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Moderate Reviews</h1>
        <p className="mt-1 text-slate-600">
          All reviews on the platform. Flagged reviews were caught by the profanity filter and are hidden
          from public view until you unflag or remove them.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {TABS.map((tab) => (
          <Button
            key={tab.label}
            variant={flaggedOnly === tab.value ? 'primary' : 'secondary'}
            onClick={() => setFlaggedOnly(tab.value)}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}
      {!loading && !error && reviews.length === 0 && <p className="text-slate-600">No reviews found.</p>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {reviews.map((review) => {
          const disabled = busyId === review.id
          return (
            <ReviewCard
              key={review.id}
              review={review}
              footer={
                <div className="flex flex-wrap gap-2 pt-1">
                  {review.flagged ? (
                    <Button disabled={disabled} onClick={() => runAction(review.id, unflagReview)}>
                      Unflag
                    </Button>
                  ) : (
                    <Button variant="secondary" disabled={disabled} onClick={() => runAction(review.id, flagReview)}>
                      Flag
                    </Button>
                  )}
                  {review.guideReply && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(review.id, removeGuideReply)}
                    >
                      Remove Reply
                    </Button>
                  )}
                  <Button variant="secondary" disabled={disabled} onClick={() => handleRemove(review.id)}>
                    Remove Review
                  </Button>
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
