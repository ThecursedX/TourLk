import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  archiveAccommodation,
  deactivateAccommodation,
  getMyAccommodations,
  markAccommodationTemporarilyUnavailable,
  reactivateAccommodation,
  resumeAccommodation,
  submitAccommodationForApproval,
} from '../../api/accommodationApi'
import AccommodationCard from '../../components/accommodations/AccommodationCard'
import Button from '../../components/ui/Button'
import { isLiveAccommodation, type AccommodationResponseDto } from '../../types/accommodation'
import { apiErrorMessage } from '../../utils/errors'

export default function MyAccommodationsPage() {
  const [accommodations, setAccommodations] = useState<AccommodationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyAccommodations()
      .then(setAccommodations)
      .catch((err) => setError(apiErrorMessage(err, 'Could not load your properties. Please try again later.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const runAction = async (
    id: number,
    action: (id: number) => Promise<AccommodationResponseDto>,
  ) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setAccommodations((prev) => prev.map((a) => (a.id === id ? updated : a)))
    } catch (err) {
      setActionError(apiErrorMessage(err, 'That action could not be completed. Please try again.'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">My Properties</h1>
          <p className="mt-1 text-slate-600">Manage the accommodations you've listed.</p>
        </div>
        <Link to="/accommodations/new">
          <Button>New Property</Button>
        </Link>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && accommodations.length === 0 && (
        <p className="text-slate-600">You haven't listed any properties yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {accommodations.map((accommodation) => {
          const disabled = busyId === accommodation.id
          return (
            <AccommodationCard
              key={accommodation.id}
              accommodation={accommodation}
              footer={
                <div className="flex flex-wrap gap-2">
                  {(accommodation.status === 'DRAFT' || isLiveAccommodation(accommodation.status)) && (
                    <Link to={`/accommodations/${accommodation.id}/edit`}>
                      <Button variant="secondary" disabled={disabled}>
                        Manage
                      </Button>
                    </Link>
                  )}
                  {accommodation.status === 'DRAFT' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, submitAccommodationForApproval)}
                    >
                      Submit
                    </Button>
                  )}
                  {(accommodation.status === 'ACTIVE' || accommodation.status === 'FULLY_BOOKED') && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, markAccommodationTemporarilyUnavailable)}
                    >
                      Pause bookings
                    </Button>
                  )}
                  {accommodation.status === 'TEMPORARILY_UNAVAILABLE' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, resumeAccommodation)}
                    >
                      Resume bookings
                    </Button>
                  )}
                  {isLiveAccommodation(accommodation.status) && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, deactivateAccommodation)}
                    >
                      Deactivate
                    </Button>
                  )}
                  {accommodation.status === 'INACTIVE' && accommodation.deactivatedByAdmin && (
                    <span className="self-center text-sm text-slate-600">Deactivated by admin</span>
                  )}
                  {accommodation.status === 'INACTIVE' && !accommodation.deactivatedByAdmin && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, reactivateAccommodation)}
                    >
                      Reactivate
                    </Button>
                  )}
                  {accommodation.status !== 'ARCHIVED' &&
                    !(accommodation.status === 'INACTIVE' && accommodation.deactivatedByAdmin) && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(accommodation.id, archiveAccommodation)}
                    >
                      Archive
                    </Button>
                  )}
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
