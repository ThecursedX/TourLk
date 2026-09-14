import { useEffect, useState } from 'react'
import {
  approveAccommodation,
  getPendingApprovalAccommodations,
  rejectAccommodation,
} from '../../api/accommodationApi'
import AccommodationCard from '../../components/accommodations/AccommodationCard'
import Button from '../../components/ui/Button'
import type { AccommodationResponseDto } from '../../types/accommodation'

export default function AdminAccommodationApprovalsPage() {
  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getPendingApprovalAccommodations()
      .then(setAccommodations)
      .catch(() => setError('Could not load pending properties. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleDecision = async (
    id: number,
    decision: (id: number) => Promise<AccommodationResponseDto>,
  ) => {
    setActionError(null)
    setBusyId(id)
    try {
      await decision(id)
      setAccommodations((prev) => prev.filter((a) => a.id !== id))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Property Approvals</h1>
        <p className="mt-1 text-slate-600">Accommodations awaiting approval.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && accommodations.length === 0 && (
        <p className="text-slate-600">No properties are waiting for approval.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {accommodations.map((accommodation) => {
          const disabled = busyId === accommodation.id
          return (
            <AccommodationCard
              key={accommodation.id}
              accommodation={accommodation}
              footer={
                <div className="flex gap-2">
                  <Button
                    disabled={disabled}
                    onClick={() => handleDecision(accommodation.id, approveAccommodation)}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={disabled}
                    onClick={() => handleDecision(accommodation.id, rejectAccommodation)}
                  >
                    Reject
                  </Button>
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
