import { useEffect, useState } from 'react'
import { getReviewEditHistory } from '../../api/reviewApi'
import Button from '../ui/Button'
import StarRating from './StarRating'
import type { ReviewEditHistoryResponseDto } from '../../types/review'

interface ReviewEditHistoryModalProps {
  reviewId: number
  onClose: () => void
}

export default function ReviewEditHistoryModal({ reviewId, onClose }: ReviewEditHistoryModalProps) {
  const [history, setHistory] = useState<ReviewEditHistoryResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getReviewEditHistory(reviewId)
      .then(setHistory)
      .catch(() => setError('Could not load edit history. Please try again later.'))
      .finally(() => setLoading(false))
  }, [reviewId])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="max-h-[80vh] w-full max-w-md overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-soft"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="font-display text-lg font-bold text-slate-900">Edit History</h2>
          <Button type="button" variant="ghost" onClick={onClose} aria-label="Close">
            &times;
          </Button>
        </div>

        {loading && <p className="text-sm text-slate-600">Loading...</p>}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && history.length === 0 && (
          <p className="text-sm text-slate-600">This review has never been edited.</p>
        )}

        <ul className="flex flex-col gap-3">
          {history.map((entry) => (
            <li key={entry.id} className="rounded-xl border border-slate-200 p-3">
              <div className="mb-1 flex items-center justify-between gap-2">
                <StarRating value={entry.oldRating} size="sm" />
                <span className="text-xs text-slate-500">{new Date(entry.editedAt).toLocaleString()}</span>
              </div>
              {entry.oldComment && <p className="text-sm text-slate-700">{entry.oldComment}</p>}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
