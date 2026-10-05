import { useEffect, useState, type FormEvent } from 'react'
import {
  browseAccommodations,
  type AccommodationSort,
  type BrowseAccommodationsOptions,
} from '../../api/accommodationApi'
import AccommodationCard from '../../components/accommodations/AccommodationCard'
import DestinationSelect from '../../components/destinations/DestinationSelect'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import type { AccommodationResponseDto } from '../../types/accommodation'
import { apiErrorMessage } from '../../utils/errors'

const SORT_OPTIONS: { value: AccommodationSort | ''; label: string }[] = [
  { value: '', label: 'Default' },
  { value: 'name', label: 'Name A-Z' },
  { value: 'price_asc', label: 'Price low to high' },
  { value: 'price_desc', label: 'Price high to low' },
  { value: 'stars_desc', label: 'Stars high to low' },
]

export default function AccommodationListPage() {
  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [locationId, setLocationId] = useState<number | ''>('')
  const [q, setQ] = useState('')
  const [minStars, setMinStars] = useState('')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [sort, setSort] = useState<AccommodationSort | ''>('')
  const [priceError, setPriceError] = useState<string | null>(null)

  const load = (options: BrowseAccommodationsOptions = {}) => {
    setLoading(true)
    setError(null)
    browseAccommodations(options)
      .then(setAccommodations)
      .catch((err) => setError(apiErrorMessage(err, 'Could not load accommodations. Please try again later.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()

    const min = minPrice.trim() === '' ? undefined : Number(minPrice)
    const max = maxPrice.trim() === '' ? undefined : Number(maxPrice)
    if ((min !== undefined && (Number.isNaN(min) || min < 0)) || (max !== undefined && (Number.isNaN(max) || max < 0))) {
      setPriceError('Prices must be zero or more')
      return
    }
    if (min !== undefined && max !== undefined && min > max) {
      setPriceError('Min price must not be greater than max price')
      return
    }
    setPriceError(null)

    load({
      locationId: locationId === '' ? undefined : locationId,
      q,
      minStars: minStars === '' ? undefined : Number(minStars),
      minPrice: min,
      maxPrice: max,
      sort: sort === '' ? undefined : sort,
    })
  }

  const handleReset = () => {
    setLocationId('')
    setQ('')
    setMinStars('')
    setMinPrice('')
    setMaxPrice('')
    setSort('')
    setPriceError(null)
    load()
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Stays</h1>
        <p className="mt-1 text-slate-600">Browse accommodations available to book.</p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-col gap-3" noValidate>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="min-w-0">
            <Input
              id="name-filter"
              label="Name"
              type="search"
              placeholder="Search by name"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <div className="min-w-0">
            <DestinationSelect
              id="location-filter"
              label="Location"
              placeholder="All locations"
              value={locationId}
              onChange={setLocationId}
            />
          </div>
          <div className="min-w-0">
            <Select
              id="stars-filter"
              label="Min stars"
              value={minStars}
              onChange={(e) => setMinStars(e.target.value)}
            >
              <option value="">Any</option>
              <option value="1">1+</option>
              <option value="2">2+</option>
              <option value="3">3+</option>
              <option value="4">4+</option>
              <option value="5">5</option>
            </Select>
          </div>
          <div className="min-w-0">
            <Select
              id="sort-filter"
              label="Sort"
              value={sort}
              onChange={(e) => setSort(e.target.value as AccommodationSort | '')}
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="min-w-0">
            <Input
              id="min-price-filter"
              label="Min price / night"
              type="number"
              min={0}
              step="any"
              inputMode="decimal"
              placeholder="0"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
            />
          </div>
          <div className="min-w-0">
            <Input
              id="max-price-filter"
              label="Max price / night"
              type="number"
              min={0}
              step="any"
              inputMode="decimal"
              placeholder="Any"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
            />
          </div>
        </div>
        {priceError && <p className="text-sm text-red-600">{priceError}</p>}

        <div className="flex flex-wrap gap-3">
          <Button type="submit">Search</Button>
          <Button type="button" variant="secondary" onClick={handleReset}>
            Reset
          </Button>
        </div>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && accommodations.length === 0 && (
        <p className="text-slate-600">No accommodations match your search.</p>
      )}
      {!loading && !error && accommodations.length > 0 && (
        <p className="text-sm text-slate-600">
          {accommodations.length} {accommodations.length === 1 ? 'stay' : 'stays'} found
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {accommodations.map((accommodation) => (
          <AccommodationCard key={accommodation.id} accommodation={accommodation} />
        ))}
      </div>
    </div>
  )
}
