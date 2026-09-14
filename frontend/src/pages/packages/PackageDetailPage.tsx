import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getPackageById } from '../../api/tourPackageApi'
import { useAuthStore } from '../../auth/authStore'
import BookNowForm from '../../components/bookings/BookNowForm'
import Card from '../../components/ui/Card'
import StatusBadge from '../../components/packages/StatusBadge'
import ReviewList from '../../components/reviews/ReviewList'
import type { TourPackageResponseDto } from '../../types/tourPackage'

export default function PackageDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tourPackage, setTourPackage] = useState<TourPackageResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getPackageById(Number(id))
      .then(setTourPackage)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 404) {
          setError('This tour package could not be found.')
        } else {
          setError('Could not load this tour package. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!tourPackage) return null

  return (
    <div className="flex flex-col gap-4">
      <Link to="/packages" className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to packages
      </Link>
      <Card className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{tourPackage.title}</h1>
          <StatusBadge status={tourPackage.status} />
        </div>
        <p className="text-slate-600">
          {tourPackage.destination.name}
          <span className="text-slate-400"> · {tourPackage.destination.region}</span>
        </p>
        <p className="whitespace-pre-line text-slate-700">{tourPackage.description}</p>
        <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-4">
          <div>
            <dt className="text-xs uppercase text-slate-500">Duration</dt>
            <dd className="text-slate-900">{tourPackage.durationDays} days</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Price</dt>
            <dd className="text-slate-900">
              {tourPackage.price.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Max capacity</dt>
            <dd className="text-slate-900">{tourPackage.maxCapacity}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Created by</dt>
            <dd className="text-slate-900">{tourPackage.createdByName}</dd>
          </div>
        </div>
      </Card>

      {tourPackage.status === 'ACTIVE' && (
        <Card className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold text-slate-900">Book this package</h2>
          {!isAuthenticated && (
            <p className="text-slate-600">
              Please{' '}
              <Link to="/login" className="font-medium text-blue-600 hover:underline">
                log in
              </Link>{' '}
              to book this package.
            </p>
          )}
          {isAuthenticated && user?.role === 'TOURIST' && (
            <BookNowForm tourPackageId={tourPackage.id} />
          )}
        </Card>
      )}

      <ReviewList reviewableType="TOUR_PACKAGE" reviewableId={tourPackage.id} />
    </div>
  )
}
