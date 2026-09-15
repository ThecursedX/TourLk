import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { browseDestinations } from '../../api/destinationApi'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import type { DestinationResponseDto } from '../../types/destination'

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
      <div className="flex flex-col gap-8 pb-10">
        {/* Header Section */}
        <div className="flex flex-col gap-2">
          <h1 className="text-3xl font-bold text-slate-900">Destinations</h1>
          <p className="text-slate-600">
            Explore the most beautiful and breathtaking places across Sri Lanka.
          </p>
        </div>

        {/* Search Filter */}
        <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow-sm border border-slate-100">
          <div className="w-full sm:w-72">
            <Input
                id="destination-search"
                label="Search Destinations"
                placeholder="e.g. Ella, Sigiriya..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Button type="submit" className="w-full sm:w-auto">Search</Button>
          <Button type="button" variant="secondary" onClick={handleReset} className="w-full sm:w-auto">
            Reset
          </Button>
        </form>

        {/* Loading & Error States */}
        {loading && (
            <div className="flex justify-center py-10">
              <p className="text-lg font-medium text-slate-500 animate-pulse">Loading destinations...</p>
            </div>
        )}
        {error && <p className="text-red-600 bg-red-50 p-4 rounded-lg">{error}</p>}

        {!loading && !error && destinations.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500">
              <svg className="h-16 w-16 mb-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <p className="text-lg font-medium">No destinations match your search.</p>
              <p className="text-sm">Try searching with a different keyword.</p>
            </div>
        )}

        {/* Destinations Grid */}
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {destinations.map((destination) => (
              <div
                  key={destination.id}
                  className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-all hover:-translate-y-1 hover:shadow-xl"
              >
                {/* Image Area */}
                <div className="relative h-48 w-full bg-slate-100 overflow-hidden">
                  {destination.imageUrl ? (
                      <img
                          src={destination.imageUrl}
                          alt={destination.name}
                          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                      />
                  ) : (
                      <div className="flex h-full w-full flex-col items-center justify-center bg-slate-100 text-slate-400">
                        <svg className="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <span className="text-xs font-medium">No Image</span>
                      </div>
                  )}

                  {/* Category Badge */}
                  {destination.category && (
                      <span className="absolute right-3 top-3 rounded-full bg-white/90 px-3 py-1 text-xs font-bold tracking-wide text-slate-800 backdrop-blur-sm shadow-sm">
                  {destination.category}
                </span>
                  )}
                </div>

                {/* Content Area */}
                <div className="flex flex-1 flex-col p-5">
                  <h3 className="text-xl font-bold text-slate-900 line-clamp-1">{destination.name}</h3>

                  {/* Location Details */}
                  <div className="mt-2 flex items-center text-sm font-medium text-slate-500">
                    <svg className="mr-1.5 h-4 w-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    <span className="line-clamp-1">
                  {destination.district ? `${destination.district}, ` : ''}{destination.region}
                </span>
                  </div>

                  {/* Best Time To Visit */}
                  {destination.bestTimeToVisit && (
                      <div className="mt-2 flex items-center text-xs font-semibold text-emerald-600 bg-emerald-50 w-fit px-2 py-1 rounded-md">
                        <svg className="mr-1.5 h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        Best time: {destination.bestTimeToVisit}
                      </div>
                  )}

                  {/* Description */}
                  {destination.description && (
                      <p className="mt-3 line-clamp-2 text-sm text-slate-600 leading-relaxed">
                        {destination.description}
                      </p>
                  )}

                  {/* Action Links */}
                  <div className="mt-auto pt-5 flex items-center justify-between border-t border-slate-100">
                    <Link
                        to="/packages"
                        className="flex items-center text-sm font-bold text-blue-600 transition-colors hover:text-blue-800"
                    >
                      Explore Packages
                      <svg className="ml-1 h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </Link>
                    <Link
                        to="/accommodations"
                        className="text-sm font-bold text-slate-500 transition-colors hover:text-slate-800"
                    >
                      Find Stays
                    </Link>
                  </div>
                </div>
              </div>
          ))}
        </div>
      </div>
  )
}