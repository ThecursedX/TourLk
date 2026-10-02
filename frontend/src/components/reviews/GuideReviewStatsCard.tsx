import { useEffect, useState } from 'react'
import { getGuideReviewStats } from '../../api/reviewApi'
import Card from '../ui/Card'
import StarRating from './StarRating'
import type { GuideReviewStatsDto } from '../../types/review'

export default function GuideReviewStatsCard() {
  const [stats, setStats] = useState<GuideReviewStatsDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getGuideReviewStats()
      .then(setStats)
      .catch(() => setError('Could not load your review stats.'))
  }, [])

  if (error) {
    return null
  }

  if (!stats) {
    return null
  }

  if (stats.totalReviews === 0) {
    return (
      <Card>
        <h2 className="font-display text-lg font-bold text-slate-900">Review Stats</h2>
        <p className="mt-2 text-sm text-slate-600">No reviews yet on your tour packages.</p>
      </Card>
    )
  }

  const maxCount = Math.max(...Object.values(stats.countByStar), 1)

  return (
    <Card>
      <h2 className="mb-4 font-display text-lg font-bold text-slate-900">Review Stats</h2>

      <div className="mb-4 flex items-center gap-3">
        <span className="font-display text-3xl font-extrabold text-slate-900">
          {stats.averageRating.toFixed(1)}
        </span>
        <div className="flex flex-col gap-1">
          <StarRating value={Math.round(stats.averageRating)} size="sm" />
          <span className="text-xs text-slate-500">
            {stats.totalReviews} review{stats.totalReviews === 1 ? '' : 's'}
          </span>
        </div>
      </div>

      <div className="mb-4 flex flex-col gap-1">
        {[5, 4, 3, 2, 1].map((star) => {
          const count = stats.countByStar[star] ?? 0
          const widthPercent = (count / maxCount) * 100
          return (
            <div key={star} className="flex items-center gap-2 text-xs text-slate-600">
              <span className="w-8 shrink-0">{star}&nbsp;star</span>
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div className="h-full rounded-full bg-amber-400" style={{ width: `${widthPercent}%` }} />
              </div>
              <span className="w-6 shrink-0 text-right">{count}</span>
            </div>
          )
        })}
      </div>

      <p className="text-sm text-slate-600">
        You&apos;ve replied to <span className="font-semibold text-slate-900">{stats.replyRate.toFixed(0)}%</span> of
        your reviews.
      </p>
    </Card>
  )
}
