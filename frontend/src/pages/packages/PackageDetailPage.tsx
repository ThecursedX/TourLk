import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getPackageById } from '../../api/tourPackageApi'
import { useAuthStore } from '../../auth/authStore'
import BookNowForm from '../../components/bookings/BookNowForm'
import DestinationClosureBanner from '../../components/destinations/DestinationClosureBanner'
import Card from '../../components/ui/Card'
import StatusBadge from '../../components/packages/StatusBadge'
import ReviewList from '../../components/reviews/ReviewList'
import { formatProvince } from '../../types/destination'
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
        {tourPackage.imageUrls.length > 0 && (
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <img
              src={tourPackage.imageUrls[0]}
              alt={tourPackage.title}
              className="h-64 w-full rounded-xl object-cover sm:col-span-2 sm:h-72"
            />
            {tourPackage.imageUrls.length > 1 && (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-1">
                {tourPackage.imageUrls.slice(1, 3).map((url, i) => (
                  <img
                    key={url}
                    src={url}
                    alt={`${tourPackage.title} ${i + 2}`}
                    className="h-32 w-full rounded-xl object-cover sm:h-[136px]"
                  />
                ))}
              </div>
            )}
          </div>
        )}

        <div className="flex items-start justify-between gap-2">
          <h1 className="text-2xl font-semibold text-slate-900">{tourPackage.title}</h1>
          <StatusBadge status={tourPackage.status} />
        </div>
        <p className="text-slate-600">
          {tourPackage.destination.name}
          <span className="text-slate-400">
            {' '}
            · {tourPackage.destination.district}, {formatProvince(tourPackage.destination.province)}
          </span>
        </p>
        <DestinationClosureBanner destination={tourPackage.destination} />
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

        {(tourPackage.inclusions.length > 0 || tourPackage.exclusions.length > 0) && (
          <div className="grid grid-cols-1 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-2">
            {tourPackage.inclusions.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Included</h3>
                <ul className="mt-2 flex flex-col gap-1">
                  {tourPackage.inclusions.map((item) => (
                    <li key={item} className="flex items-start gap-2 text-sm text-slate-700">
                      <span className="mt-0.5 text-green-600">✓</span>
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {tourPackage.exclusions.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Not included</h3>
                <ul className="mt-2 flex flex-col gap-1">
                  {tourPackage.exclusions.map((item) => (
                    <li key={item} className="flex items-start gap-2 text-sm text-slate-700">
                      <span className="mt-0.5 text-red-500">&times;</span>
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Card>

      {tourPackage.itineraryDays.length > 0 && (
        <Card className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold text-slate-900">Itinerary</h2>
          <ol className="flex flex-col gap-4">
            {tourPackage.itineraryDays.map((day) => (
              <li key={day.id} className="flex gap-4 border-l-2 border-blue-200 pl-4">
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-semibold uppercase tracking-wide text-blue-600">
                    Day {day.dayNumber}
                  </span>
                  <h3 className="font-medium text-slate-900">{day.title}</h3>
                  {day.description && <p className="text-sm text-slate-600">{day.description}</p>}
                  {day.placesToVisit.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {day.placesToVisit.map((place) => (
                        <span
                          key={place}
                          className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600"
                        >
                          {place}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </Card>
      )}

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
            <BookNowForm
              tourPackageId={tourPackage.id}
              hasDepartures={tourPackage.hasDepartures}
              destination={tourPackage.destination}
              durationDays={tourPackage.durationDays}
            />
          )}
        </Card>
      )}

      <ReviewList
        reviewableType="TOUR_PACKAGE"
        reviewableId={tourPackage.id}
        canReply={
          isAuthenticated &&
          (user?.role === 'ADMIN' || (user?.role === 'GUIDE' && user?.userId === tourPackage.createdById))
        }
      />
    </div>
  )
}
