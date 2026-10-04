import { useState } from 'react'
import { replyToReview } from '../../api/reviewApi'
import Button from '../ui/Button'
import type { ReviewResponseDto } from '../../types/review'

interface GuideReplyFormProps {
  review: ReviewResponseDto
  onUpdated: (review: ReviewResponseDto) => void
}

export default function GuideReplyForm({ review, onUpdated }: GuideReplyFormProps) {
  const [editing, setEditing] = useState(false)
  const [reply, setReply] = useState(review.guideReply ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async () => {
    if (!reply.trim()) {
      setError('Reply cannot be empty')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      const updated = await replyToReview(review.id, reply.trim())
      onUpdated(updated)
      setEditing(false)
    } catch {
      setError('Could not post your reply. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!editing) {
    return (
      <div className="pt-1">
        <Button variant="secondary" onClick={() => setEditing(true)}>
          {review.guideReply ? 'Edit Reply' : 'Reply'}
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-2 pt-1">
      <textarea
        rows={2}
        value={reply}
        onChange={(e) => setReply(e.target.value)}
        maxLength={1000}
        placeholder="Write a reply to this review..."
        className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div className="flex gap-2">
        <Button disabled={submitting} onClick={handleSubmit}>
          {submitting ? 'Saving...' : 'Post Reply'}
        </Button>
        <Button variant="secondary" disabled={submitting} onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
    </div>
  )
}
