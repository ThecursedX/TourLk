import { useEffect, useState } from 'react'
import {
  deleteReview,
  getAllReviewsForAdmin,
  hideReview,
  removeGuideReply,
  reportReview,
  unhideReview,
  unreportReview,
} from '../../api/reviewApi'
import ReviewCard from '../../components/reviews/ReviewCard'
import ReviewEditHistoryModal from '../../components/reviews/ReviewEditHistoryModal'
import Button from '../../components/ui/Button'
import type { ReviewResponseDto, ReviewStatus } from '../../types/review'

const TABS: { label: string; value: ReviewStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Published', value: 'PUBLISHED' },
  { label: 'Edited', value: 'EDITED' },
  { label: 'Reported', value: 'REPORTED' },
  { label: 'Hidden', value: 'HIDDEN' },
  { label: 'Deleted', value: 'DELETED' },
]

export default function AdminReviewsPage() {
  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [status, setStatus] = useState<ReviewStatus | undefined>(undefined)
  const [historyId, setHistoryId] = useState<number | null>(null)

  const load = (filter?: ReviewStatus) => {
    setLoading(true)
    setError(null)
    getAllReviewsForAdmin(filter)
      .then(setReviews)
      .catch(() => setError('Could not load reviews. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load(status)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status])

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
    if (!window.confirm('Remove this review? It will be hidden from public view and marked as deleted.')) return
    setActionError(null)
    setBusyId(id)
    try {
      await deleteReview(id)
      load(status)
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
          All reviews on the platform. Reported reviews were auto-flagged for profanity or reported by an admin.
          Hidden and deleted reviews are excluded from public listings and rating averages.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {TABS.map((tab) => (
          <Button
            key={tab.label}
            variant={status === tab.value ? 'primary' : 'secondary'}
            onClick={() => setStatus(tab.value)}
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
                  {review.status === 'REPORTED' ? (
                    <Button disabled={disabled} onClick={() => runAction(review.id, unreportReview)}>
                      Unreport
                    </Button>
                  ) : (
                    <Button variant="secondary" disabled={disabled} onClick={() => runAction(review.id, reportReview)}>
                      Report
                    </Button>
                  )}
                  {review.status === 'HIDDEN' ? (
                    <Button disabled={disabled} onClick={() => runAction(review.id, unhideReview)}>
                      Unhide
                    </Button>
                  ) : (
                    <Button variant="secondary" disabled={disabled} onClick={() => runAction(review.id, hideReview)}>
                      Hide
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
                  <Button variant="ghost" onClick={() => setHistoryId(review.id)}>
                    History
                  </Button>
                  {review.status !== 'DELETED' && (
                    <Button variant="secondary" disabled={disabled} onClick={() => handleRemove(review.id)}>
                      Delete
                    </Button>
                  )}
                </div>
              }
            />
          )
        })}
      </div>

      {historyId !== null && (
        <ReviewEditHistoryModal reviewId={historyId} onClose={() => setHistoryId(null)} />
      )}
    </div>
  )
}
