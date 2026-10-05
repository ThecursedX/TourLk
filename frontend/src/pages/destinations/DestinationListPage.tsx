import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { browseDestinations } from '../../api/destinationApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import DestinationClosureBanner from '../../components/destinations/DestinationClosureBanner'
import CardImage from '../../components/ui/CardImage'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { formatProvince, type DestinationResponseDto } from '../../types/destination'

export default function DestinationListPage() {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [radiusKm, setRadiusKm] = useState(50)
  const [locating, setLocating] = useState(false)
  const [nearbyActive, setNearbyActive] = useState(false)

  const load = (query?: string) => {
    setLoading(true)
    setError(null)
    setNearbyActive(false)
    browseDestinations(query ? { search: query } : undefined)
      .then(setDestinations)
      .catch(() => setError('Could not load destinations. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load(search.trim() || undefined)
  }

  /** Searches around the browser's location; results come back nearest first. */
  const handleNearMe = () => {
    if (!navigator.geolocation) {
      setError('Your browser does not support location lookup.')
      return
    }
    setLocating(true)
    setError(null)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLoading(true)
        browseDestinations({ nearby: `${coords.latitude},${coords.longitude}`, radiusKm })
          .then((results) => {
            setDestinations(results)
            setNearbyActive(true)
          })
          .catch(() => setError('Could not search nearby destinations. Please try again later.'))
          .finally(() => {
            setLoading(false)
            setLocating(false)
          })
      },
      () => {
        setError('We could not get your location. Allow location access and try again.')
        setLocating(false)
      },
    )
  }

  const handleReset = () => {
    setSearch('')
    load()
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Destinations</h1>
        <p className="mt-1 text-slate-600">
          Explore the places you can visit across Sri Lanka.
        </p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3">
        <div className="w-64">
          <Input
            id="destination-search"
            label="Search"
            placeholder="e.g. Ella"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Button type="submit">Search</Button>
        <div className="w-36">
          <Select
            id="radius"
            label="Radius"
            value={radiusKm}
            onChange={(e) => setRadiusKm(Number(e.target.value))}
          >
            {[10, 25, 50, 100, 200].map((km) => (
              <option key={km} value={km}>
                {km} km
              </option>
            ))}
          </Select>
        </div>
        <Button type="button" variant="secondary" onClick={handleNearMe} disabled={locating}>
          {locating ? 'Locating...' : 'Near me'}
        </Button>
        <Button type="button" variant="secondary" onClick={handleReset}>
          Reset
        </Button>
      </form>

      {nearbyActive && !loading && (
        <p className="text-sm text-slate-600">
          Showing destinations within {radiusKm} km of your location, nearest first.
        </p>
      )}

      {loading && <p className="text-slate-600">Loading destinations...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && destinations.length === 0 && (
        <p className="text-slate-600">
          {nearbyActive ? 'No destinations with a known location were found nearby.' : 'No destinations match your search.'}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {destinations.map((destination) => (
          <Card key={destination.id} className="flex flex-col gap-3">
            <DestinationClosureBanner destination={destination} compact />
            <CardImage urls={destination.imageUrls} alt={destination.name} />
            <div>
              <Link to={`/destinations/${destination.id}`}>
                <h3 className="text-lg font-semibold text-slate-900 hover:text-blue-700">{destination.name}</h3>
              </Link>
              <p className="text-sm text-slate-500">
                {destination.district}, {formatProvince(destination.province)}
                {destination.distanceKm != null && (
                  <span className="text-slate-400"> · {destination.distanceKm} km away</span>
                )}
              </p>
              <span className="mt-1 inline-block rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                {destination.category}
              </span>
            </div>
            {destination.description && (
              <p className="line-clamp-3 text-sm text-slate-600">{destination.description}</p>
            )}
            {destination.bestTimeToVisit && (
              <p className="text-xs text-slate-500">Best time to visit: {destination.bestTimeToVisit}</p>
            )}
            <div className="mt-auto flex gap-4 pt-1 text-sm font-medium text-blue-600">
              <Link to={`/destinations/${destination.id}`} className="hover:underline">
                View destination
              </Link>
              <Link to={`/packages?destinationId=${destination.id}`} className="hover:underline">
                Packages
              </Link>
              <Link to="/accommodations" className="hover:underline">
                Stays
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
