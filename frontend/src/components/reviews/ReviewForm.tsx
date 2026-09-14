import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { createReview, updateReview } from '../../api/reviewApi'
import Button from '../ui/Button'
import StarRating from './StarRating'
import type { ErrorResponse } from '../../types/auth'
import type { ReviewResponseDto, ReviewableType } from '../../types/review'

interface ReviewFormProps {
  reviewableType: ReviewableType
  reviewableId: number
  sourceBookingId: number
  existingReviewId?: number
  initialRating?: number
  initialComment?: string
  onSuccess: (review: ReviewResponseDto) => void
  onCancel?: () => void
}

export default function ReviewForm({
  reviewableType,
  reviewableId,
  sourceBookingId,
  existingReviewId,
  initialRating = 0,
  initialComment = '',
  onSuccess,
  onCancel,
}: ReviewFormProps) {
  const [rating, setRating] = useState(initialRating)
  const [comment, setComment] = useState(initialComment)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (rating < 1) {
      setFormError('Please select a star rating')
      return
    }

    setSubmitting(true)
    try {
      const request = {
        reviewableType,
        reviewableId,
        sourceBookingId,
        rating,
        comment: comment.trim() || undefined,
      }
      const review = existingReviewId
        ? await updateReview(existingReviewId, request)
        : await createReview(request)
      onSuccess(review)
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(err.response.data.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
      <StarRating value={rating} onChange={setRating} />
      <textarea
        rows={3}
        placeholder="Share your experience (optional)"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Saving...' : existingReviewId ? 'Save Changes' : 'Submit Review'}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
