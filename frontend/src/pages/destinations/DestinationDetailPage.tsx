import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getDestinationById } from '../../api/destinationApi'
import { browseAccommodations } from '../../api/accommodationApi'
import AccommodationCard from '../../components/accommodations/AccommodationCard'
import DestinationClosureBanner from '../../components/destinations/DestinationClosureBanner'
import LocationMap from '../../components/maps/LocationMap'
import Card from '../../components/ui/Card'
import { formatProvince, hasCoordinates, type DestinationResponseDto } from '../../types/destination'
import type { AccommodationResponseDto } from '../../types/accommodation'

export default function DestinationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [destination, setDestination] = useState<DestinationResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [accommodationsLoading, setAccommodationsLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getDestinationById(Number(id))
      .then(setDestination)
      .catch((err) => {
        if (isAxiosError(err) && err.response?.status === 404) {
          setError('This destination could not be found.')
        } else {
          setError('Could not load this destination. Please try again later.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    setAccommodationsLoading(true)
    browseAccommodations({ locationId: Number(id) })
      .then(setAccommodations)
      .catch(() => setAccommodations([]))
      .finally(() => setAccommodationsLoading(false))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!destination) return null

  const gallery = destination.imageUrls

  return (
    <div className="flex flex-col gap-6">
      <Link to="/destinations" className="text-sm font-medium text-blue-600 hover:underline">
        &larr; Back to destinations
      </Link>

      <DestinationClosureBanner destination={destination} />

      <Card className="flex flex-col gap-4">
        {gallery.length > 0 && (
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <img
              src={gallery[0]}
              alt={destination.name}
              className="h-64 w-full rounded-xl object-cover sm:col-span-2 sm:h-72"
            />
            {gallery.length > 1 && (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-1">
                {gallery.slice(1, 3).map((url, i) => (
                  <img
                    key={url}
                    src={url}
                    alt={`${destination.name} ${i + 2}`}
                    className="h-32 w-full rounded-xl object-cover sm:h-[136px]"
                  />
                ))}
              </div>
            )}
          </div>
        )}

        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{destination.name}</h1>
            <p className="mt-1 text-slate-600">
              {destination.district}, {formatProvince(destination.province)}
            </p>
          </div>
          <span className="inline-block rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700">
            {destination.category}
          </span>
        </div>

        {destination.description && <p className="whitespace-pre-line text-slate-700">{destination.description}</p>}

        <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-3">
          {destination.bestTimeToVisit && (
            <div>
              <dt className="text-xs uppercase text-slate-500">Best time to visit</dt>
              <dd className="text-slate-900">{destination.bestTimeToVisit}</dd>
            </div>
          )}
          {destination.openingHours && (
            <div>
              <dt className="text-xs uppercase text-slate-500">Opening hours</dt>
              <dd className="text-slate-900">{destination.openingHours}</dd>
            </div>
          )}
          {destination.entryFee != null && (
            <div>
              <dt className="text-xs uppercase text-slate-500">Entry fee</dt>
              <dd className="text-slate-900">
                {destination.entryFee === 0
                  ? 'Free'
                  : destination.entryFee.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
              </dd>
            </div>
          )}
          <div>
            <dt className="text-xs uppercase text-slate-500">Active tour packages</dt>
            <dd className="text-slate-900">{destination.activePackageCount ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">Stays in this area</dt>
            <dd className="text-slate-900">{destination.activeAccommodationCount ?? accommodations.length}</dd>
          </div>
        </div>

        {destination.visitorRules && (
          <div className="border-t border-slate-200 pt-4">
            <h2 className="mb-1 text-xs uppercase text-slate-500">Visitor rules &amp; guidelines</h2>
            <p className="whitespace-pre-line text-sm text-slate-700">{destination.visitorRules}</p>
          </div>
        )}

        {hasCoordinates(destination) && (
          <div className="border-t border-slate-200 pt-4">
            <h2 className="mb-2 text-xs uppercase text-slate-500">Location</h2>
            <LocationMap
              name={destination.name}
              latitude={destination.latitude}
              longitude={destination.longitude}
            />
          </div>
        )}

        <div className="flex gap-4 border-t border-slate-200 pt-4 text-sm font-medium text-blue-600">
          <Link to={`/packages?destinationId=${destination.id}`} className="hover:underline">
            Browse tour packages &rarr;
          </Link>
        </div>
      </Card>

      <div className="flex flex-col gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Stays in {destination.name}</h2>
          <p className="mt-1 text-sm text-slate-600">Accommodations available in this area.</p>
        </div>

        {accommodationsLoading && <p className="text-slate-600">Loading accommodations...</p>}

        {!accommodationsLoading && accommodations.length === 0 && (
          <p className="text-slate-600">No accommodations are listed in this area yet.</p>
        )}

        {!accommodationsLoading && accommodations.length > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {accommodations.map((accommodation) => (
              <AccommodationCard key={accommodation.id} accommodation={accommodation} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
