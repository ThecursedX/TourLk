import { useEffect, useState, type FormEvent } from 'react'
import { browseAccommodations } from '../../api/accommodationApi'
import AccommodationCard from '../../components/accommodations/AccommodationCard'
import DestinationSelect from '../../components/destinations/DestinationSelect'
import Button from '../../components/ui/Button'
import type { AccommodationResponseDto } from '../../types/accommodation'

export default function AccommodationListPage() {
  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [locationId, setLocationId] = useState<number | ''>('')

  const load = (filterLocationId?: number) => {
    setLoading(true)
    setError(null)
    browseAccommodations(filterLocationId)
      .then(setAccommodations)
      .catch(() => setError('Could not load accommodations. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load(locationId === '' ? undefined : locationId)
  }

  const handleReset = () => {
    setLocationId('')
    load()
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Stays</h1>
        <p className="mt-1 text-slate-600">Browse accommodations available to book.</p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3">
        <div className="w-56">
          <DestinationSelect
            id="location-filter"
            label="Location"
            placeholder="All locations"
            value={locationId}
            onChange={setLocationId}
          />
        </div>
        <Button type="submit">Search</Button>
        <Button type="button" variant="secondary" onClick={handleReset}>
          Reset
        </Button>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && accommodations.length === 0 && (
        <p className="text-slate-600">No accommodations match your search.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {accommodations.map((accommodation) => (
          <AccommodationCard key={accommodation.id} accommodation={accommodation} />
        ))}
      </div>
    </div>
  )
}
