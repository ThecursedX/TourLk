import { useEffect, useState, type FormEvent } from 'react'
import { browsePackages } from '../../api/tourPackageApi'
import PackageCard from '../../components/packages/PackageCard'
import DestinationSelect from '../../components/destinations/DestinationSelect'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import type { TourPackageResponseDto } from '../../types/tourPackage'

export default function PackageListPage() {
  const [packages, setPackages] = useState<TourPackageResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [destinationId, setDestinationId] = useState<number | ''>('')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')

  const load = async (filters?: { destinationId?: number; minPrice?: number; maxPrice?: number }) => {
    setLoading(true)
    setError(null)
    try {
      const data = await browsePackages(filters)
      setPackages(data)
    } catch {
      setError('Could not load tour packages. Please try again later.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load({
      destinationId: destinationId === '' ? undefined : destinationId,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
    })
  }

  const handleReset = () => {
    setDestinationId('')
    setMinPrice('')
    setMaxPrice('')
    load()
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Tour Packages</h1>
        <p className="mt-1 text-slate-600">Browse active tour packages.</p>
      </div>

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3">
        <div className="w-56">
          <DestinationSelect
            id="destination-filter"
            label="Destination"
            placeholder="All destinations"
            value={destinationId}
            onChange={setDestinationId}
          />
        </div>
        <div className="w-32">
          <Input
            id="min-price-filter"
            label="Min price"
            type="number"
            min={0}
            value={minPrice}
            onChange={(e) => setMinPrice(e.target.value)}
          />
        </div>
        <div className="w-32">
          <Input
            id="max-price-filter"
            label="Max price"
            type="number"
            min={0}
            value={maxPrice}
            onChange={(e) => setMaxPrice(e.target.value)}
          />
        </div>
        <Button type="submit">Search</Button>
        <Button type="button" variant="secondary" onClick={handleReset}>
          Reset
        </Button>
      </form>

      {loading && <p className="text-slate-600">Loading packages...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && packages.length === 0 && (
        <p className="text-slate-600">No tour packages match your search.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packages.map((pkg) => (
          <PackageCard key={pkg.id} tourPackage={pkg} />
        ))}
      </div>
    </div>
  )
}
