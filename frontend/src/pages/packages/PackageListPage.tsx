import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { browsePackages } from '../../api/tourPackageApi'
import PackageCard from '../../components/packages/PackageCard'
import DestinationSelect from '../../components/destinations/DestinationSelect'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import type { ErrorResponse } from '../../types/auth'
import type {
  BudgetTier,
  PackageSort,
  TourPackageResponseDto,
  TourPackageSearchParams,
} from '../../types/tourPackage'

const BUDGET_TIERS: { value: BudgetTier; label: string }[] = [
  { value: 'BUDGET', label: 'Budget' },
  { value: 'STANDARD', label: 'Standard' },
  { value: 'LUXURY', label: 'Luxury' },
]

const SORTS: { value: PackageSort; label: string }[] = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'duration', label: 'Duration: shortest first' },
  { value: 'rating', label: 'Top rated' },
]

/** Text form fields, one per query-string key. Kept as strings so half-typed input survives. */
interface FilterDraft {
  q: string
  destinationId: string
  minPrice: string
  maxPrice: string
  minDays: string
  maxDays: string
  budgetTier: string
  travelDate: string
}

const FILTER_KEYS: (keyof FilterDraft)[] = [
  'q',
  'destinationId',
  'minPrice',
  'maxPrice',
  'minDays',
  'maxDays',
  'budgetTier',
  'travelDate',
]

function draftFromUrl(params: URLSearchParams): FilterDraft {
  const draft = {} as FilterDraft
  for (const key of FILTER_KEYS) {
    draft[key] = params.get(key) ?? ''
  }
  return draft
}

function isSort(value: string | null): value is PackageSort {
  return SORTS.some((option) => option.value === value)
}

function isBudgetTier(value: string | null): value is BudgetTier {
  return BUDGET_TIERS.some((option) => option.value === value)
}

function nonNegativeNumber(value: string | null): number | undefined {
  if (!value) return undefined
  const n = Number(value)
  return Number.isFinite(n) && n >= 0 ? n : undefined
}

/**
 * Turns the URL into API params, dropping anything malformed (e.g. a
 * hand-edited ?budgetTier=cheap) instead of sending it and getting a 400.
 */
function apiParamsFromUrl(params: URLSearchParams): TourPackageSearchParams {
  const budgetTier = params.get('budgetTier')
  const sort = params.get('sort')
  const travelDate = params.get('travelDate')
  return {
    q: params.get('q')?.trim() || undefined,
    destinationId: nonNegativeNumber(params.get('destinationId')),
    minPrice: nonNegativeNumber(params.get('minPrice')),
    maxPrice: nonNegativeNumber(params.get('maxPrice')),
    minDays: nonNegativeNumber(params.get('minDays')),
    maxDays: nonNegativeNumber(params.get('maxDays')),
    budgetTier: isBudgetTier(budgetTier) ? budgetTier : undefined,
    travelDate: travelDate && /^\d{4}-\d{2}-\d{2}$/.test(travelDate) ? travelDate : undefined,
    sort: isSort(sort) ? sort : undefined,
  }
}

/**
 * The URL query string is the source of truth for filters and sort, so
 * searches can be bookmarked/shared and back/forward restore them. The
 * form edits a local draft that's written to the URL on Search; the sort
 * dropdown writes to the URL immediately.
 */
export default function PackageListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [packages, setPackages] = useState<TourPackageResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<FilterDraft>(() => draftFromUrl(searchParams))

  const queryString = searchParams.toString()

  // Refetch on every URL change (Search, sort, Reset, back/forward) and
  // keep the form in step with it. The `cancelled` flag drops responses
  // from superseded requests so a slow earlier search can't overwrite a
  // newer one.
  useEffect(() => {
    const params = new URLSearchParams(queryString)
    setDraft(draftFromUrl(params))

    let cancelled = false
    setLoading(true)
    setError(null)
    browsePackages(apiParamsFromUrl(params))
      .then((data) => {
        if (!cancelled) setPackages(data)
      })
      .catch((err) => {
        if (cancelled) return
        setPackages([])
        setError(
          isAxiosError<ErrorResponse>(err) && err.response?.status === 400
            ? err.response.data.message
            : 'Could not load tour packages. Please try again later.',
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [queryString])

  const setField = (key: keyof FilterDraft, value: string) => setDraft((d) => ({ ...d, [key]: value }))

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    const next = new URLSearchParams()
    for (const key of FILTER_KEYS) {
      const value = draft[key].trim()
      if (value) next.set(key, value)
    }
    const sort = searchParams.get('sort')
    if (sort) next.set('sort', sort)
    setSearchParams(next)
  }

  const handleSortChange = (sort: string) => {
    const next = new URLSearchParams(searchParams)
    if (sort) {
      next.set('sort', sort)
    } else {
      next.delete('sort')
    }
    setSearchParams(next)
  }

  const handleReset = () => {
    setSearchParams(new URLSearchParams())
  }

  const hasActiveFilters = FILTER_KEYS.some((key) => searchParams.get(key))

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Tour Packages</h1>
        <p className="mt-1 text-slate-600">Browse active tour packages.</p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-col gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-56 flex-1">
            <Input
              id="q-filter"
              label="Search"
              type="search"
              placeholder="e.g. tea, safari, beach"
              value={draft.q}
              onChange={(e) => setField('q', e.target.value)}
            />
          </div>
          <div className="w-56">
            <DestinationSelect
              id="destination-filter"
              label="Destination"
              placeholder="All destinations"
              value={draft.destinationId === '' ? '' : Number(draft.destinationId)}
              onChange={(id) => setField('destinationId', id === '' ? '' : String(id))}
            />
          </div>
          <div className="w-44">
            <Input
              id="travel-date-filter"
              label="Travelling from"
              type="date"
              value={draft.travelDate}
              onChange={(e) => setField('travelDate', e.target.value)}
            />
          </div>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-28">
            <Input
              id="min-price-filter"
              label="Min price"
              type="number"
              min={0}
              value={draft.minPrice}
              onChange={(e) => setField('minPrice', e.target.value)}
            />
          </div>
          <div className="w-28">
            <Input
              id="max-price-filter"
              label="Max price"
              type="number"
              min={0}
              value={draft.maxPrice}
              onChange={(e) => setField('maxPrice', e.target.value)}
            />
          </div>
          <div className="w-28">
            <Input
              id="min-days-filter"
              label="Min days"
              type="number"
              min={1}
              value={draft.minDays}
              onChange={(e) => setField('minDays', e.target.value)}
            />
          </div>
          <div className="w-28">
            <Input
              id="max-days-filter"
              label="Max days"
              type="number"
              min={1}
              value={draft.maxDays}
              onChange={(e) => setField('maxDays', e.target.value)}
            />
          </div>
          <div className="w-36">
            <Select
              id="budget-tier-filter"
              label="Budget"
              value={draft.budgetTier}
              onChange={(e) => setField('budgetTier', e.target.value)}
            >
              <option value="">Any budget</option>
              {BUDGET_TIERS.map((tier) => (
                <option key={tier.value} value={tier.value}>
                  {tier.label}
                </option>
              ))}
            </Select>
          </div>
          <Button type="submit">Search</Button>
          <Button type="button" variant="secondary" onClick={handleReset}>
            Reset
          </Button>
        </div>
      </form>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-600">
          {loading ? 'Loading packages...' : `${packages.length} package${packages.length === 1 ? '' : 's'}`}
        </p>
        <div className="flex items-center gap-2">
          <label htmlFor="sort" className="text-sm font-medium text-slate-700">
            Sort by
          </label>
          <Select
            id="sort"
            value={isSort(searchParams.get('sort')) ? (searchParams.get('sort') as string) : ''}
            onChange={(e) => handleSortChange(e.target.value)}
          >
            <option value="">Recommended</option>
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && packages.length === 0 && (
        <p className="text-slate-600">
          {hasActiveFilters ? 'No tour packages match your search.' : 'No tour packages are available yet.'}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packages.map((pkg) => (
          <PackageCard key={pkg.id} tourPackage={pkg} />
        ))}
      </div>
    </div>
  )
}
