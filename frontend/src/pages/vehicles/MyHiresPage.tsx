import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { cancelHire, getMyHires } from '../../api/vehicleHireApi'
import { getMyReviews } from '../../api/reviewApi'
import HireCard from '../../components/vehicles/HireCard'
import ReviewForm from '../../components/reviews/ReviewForm'
import Button from '../../components/ui/Button'
import type { VehicleHireResponseDto } from '../../types/vehicle'
import type { ReviewResponseDto } from '../../types/review'

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED'])

export default function MyHiresPage() {
  const [hires, setHires] = useState<VehicleHireResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [reviews, setReviews] = useState<ReviewResponseDto[]>([])
  const [reviewingId, setReviewingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyHires()
      .then(setHires)
      .catch(() => setError('Could not load your hires. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    getMyReviews()
      .then(setReviews)
      .catch(() => {})
  }, [])

  const hasReview = (hireId: number) =>
    reviews.some((r) => r.reviewableType === 'VEHICLE' && r.sourceBookingId === hireId)

  const handleCancel = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await cancelHire(id)
      setHires((prev) => prev.map((h) => (h.id === id ? updated : h)))
    } catch {
      setActionError('That hire could not be cancelled. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Hires</h1>
        <p className="mt-1 text-slate-600">Vehicles you've hired.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && hires.length === 0 && (
        <p className="text-slate-600">You haven't hired any vehicles yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {hires.map((hire) => (
          <HireCard
            key={hire.id}
            hire={hire}
            footer={
              <div className="flex flex-col gap-2">
                <div className="flex gap-2">
                  {hire.status === 'PENDING' && (
                    <Link to={`/checkout/vehicle_hire/${hire.id}`}>
                      <Button disabled={busyId === hire.id}>Pay Now</Button>
                    </Link>
                  )}
                  {CANCELLABLE.has(hire.status) && (
                    <Button
                      variant="secondary"
                      disabled={busyId === hire.id}
                      onClick={() => handleCancel(hire.id)}
                    >
                      Cancel
                    </Button>
                  )}
                  {hire.status === 'COMPLETED' && !hasReview(hire.id) && reviewingId !== hire.id && (
                    <Button
                      variant="secondary"
                      disabled={busyId === hire.id}
                      onClick={() => setReviewingId(hire.id)}
                    >
                      Leave a Review
                    </Button>
                  )}
                </div>
                {reviewingId === hire.id && (
                  <ReviewForm
                    reviewableType="VEHICLE"
                    reviewableId={hire.vehicle.id}
                    sourceBookingId={hire.id}
                    onSuccess={(review) => {
                      setReviews((prev) => [...prev, review])
                      setReviewingId(null)
                    }}
                    onCancel={() => setReviewingId(null)}
                  />
                )}
              </div>
            }
          />
        ))}
      </div>
    </div>
  )
}
