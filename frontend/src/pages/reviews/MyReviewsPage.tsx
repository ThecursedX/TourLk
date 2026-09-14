import { useEffect, useState } from 'react'
import { deleteReview, getMyReviews } from '../../api/reviewApi'
import ReviewCard from '../../components/reviews/ReviewCard'
import ReviewForm from '../../components/reviews/ReviewForm'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { ReviewResponseDto } from '../../types/review'

export default function MyReviewsPage() {
  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyReviews()
      .then(setReviews)
      .catch(() => setError('Could not load your reviews. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleDelete = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      await deleteReview(id)
      setReviews((prev) => prev.filter((r) => r.id !== id))
    } catch {
      setActionError('That review could not be deleted. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Reviews</h1>
        <p className="mt-1 text-slate-600">Reviews you've left.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && reviews.length === 0 && (
        <p className="text-slate-600">You haven't left any reviews yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {reviews.map((review) =>
          editingId === review.id ? (
            <Card key={review.id}>
              <ReviewForm
                reviewableType={review.reviewableType}
                reviewableId={review.reviewableId}
                sourceBookingId={review.sourceBookingId}
                existingReviewId={review.id}
                initialRating={review.rating}
                initialComment={review.comment ?? ''}
                onSuccess={(updated) => {
                  setReviews((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
                  setEditingId(null)
                }}
                onCancel={() => setEditingId(null)}
              />
            </Card>
          ) : (
            <ReviewCard
              key={review.id}
              review={review}
              footer={
                <div className="flex gap-2 pt-1">
                  <Button
                    variant="secondary"
                    disabled={busyId === review.id}
                    onClick={() => setEditingId(review.id)}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={busyId === review.id}
                    onClick={() => handleDelete(review.id)}
                  >
                    Delete
                  </Button>
                </div>
              }
            />
          ),
        )}
      </div>
    </div>
  )
}
