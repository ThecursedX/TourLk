import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { createReview, updateReview } from '../../api/reviewApi'
import Button from '../ui/Button'
import TagInput from '../ui/TagInput'
import StarRating from './StarRating'
import type { ErrorResponse } from '../../types/auth'
import type { ReviewResponseDto, ReviewableType } from '../../types/review'

const MIN_LENGTH = 10
const MAX_LENGTH = 500
const MAX_IMAGES = 5

interface ReviewFormProps {
  reviewableType: ReviewableType
  reviewableId: number
  sourceBookingId: number
  existingReviewId?: number
  initialRating?: number
  initialComment?: string
  initialImageUrls?: string[]
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
  initialImageUrls = [],
  onSuccess,
  onCancel,
}: ReviewFormProps) {
  const [rating, setRating] = useState(initialRating)
  const [comment, setComment] = useState(initialComment)
  const [imageUrls, setImageUrls] = useState<string[]>(initialImageUrls)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): string | null => {
    if (rating < 1) return 'Please select a star rating'
    const trimmed = comment.trim()
    if (trimmed.length < MIN_LENGTH) return `Review must be at least ${MIN_LENGTH} characters`
    if (trimmed.length > MAX_LENGTH) return `Review must be at most ${MAX_LENGTH} characters`
    if (imageUrls.length > MAX_IMAGES) return `You can attach at most ${MAX_IMAGES} images`
    return null
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    const validationError = validate()
    if (validationError) {
      setFormError(validationError)
      return
    }
    if (!window.confirm('Submit this review? You can edit it later from My Reviews.')) {
      return
    }

    setSubmitting(true)
    try {
      const request = {
        reviewableType,
        reviewableId,
        sourceBookingId,
        rating,
        comment: comment.trim(),
        imageUrls,
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
        placeholder="Share your experience (10-500 characters)"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        maxLength={MAX_LENGTH}
        className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <span className="text-xs text-slate-500">
        {comment.trim().length}/{MAX_LENGTH}
      </span>
      <TagInput
        label={`Photo URLs (optional, up to ${MAX_IMAGES})`}
        values={imageUrls}
        onChange={setImageUrls}
        placeholder="Paste an image URL and press Enter"
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
