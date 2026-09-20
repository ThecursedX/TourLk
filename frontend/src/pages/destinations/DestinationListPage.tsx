import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { browseDestinations } from '../../api/destinationApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { formatProvince, type DestinationResponseDto } from '../../types/destination'

export default function DestinationListPage() {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  const load = (query?: string) => {
    setLoading(true)
    setError(null)
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
        <Button type="button" variant="secondary" onClick={handleReset}>
          Reset
        </Button>
      </form>

      {loading && <p className="text-slate-600">Loading destinations...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && destinations.length === 0 && (
        <p className="text-slate-600">No destinations match your search.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {destinations.map((destination) => (
          <Card key={destination.id} className="flex flex-col gap-3">
            <Link to={`/destinations/${destination.id}`}>
              {destination.imageUrls.length > 0 && (
                <img
                  src={destination.imageUrls[0]}
                  alt={destination.name}
                  className="h-40 w-full rounded-md object-cover"
                />
              )}
            </Link>
            <div>
              <Link to={`/destinations/${destination.id}`}>
                <h3 className="text-lg font-semibold text-slate-900 hover:text-blue-700">{destination.name}</h3>
              </Link>
              <p className="text-sm text-slate-500">
                {destination.district}, {formatProvince(destination.province)}
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
              <Link to="/packages" className="hover:underline">
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
