import { useEffect, useState } from 'react'
import {
  createDestination,
  deactivateDestination,
  getAllDestinations,
  reactivateDestination,
  updateDestination,
} from '../../api/destinationApi'
import DestinationForm from '../../components/destinations/DestinationForm'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { DestinationRequestDto, DestinationResponseDto } from '../../types/destination'

export default function AdminDestinationsPage() {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getAllDestinations()
      .then(setDestinations)
      .catch(() => setError('Could not load destinations. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleCreate = async (values: DestinationRequestDto) => {
    await createDestination(values)
    setCreating(false)
    load()
  }

  const handleUpdate = async (id: number, values: DestinationRequestDto) => {
    await updateDestination(id, values)
    setEditingId(null)
    load()
  }

  const handleToggle = async (destination: DestinationResponseDto) => {
    setActionError(null)
    setBusyId(destination.id)
    try {
      const updated =
        destination.status === 'ACTIVE'
          ? await deactivateDestination(destination.id)
          : await reactivateDestination(destination.id)
      setDestinations((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Manage Destinations</h1>
          <p className="mt-1 text-slate-600">
            Curate the list of places tourists can browse and filter by.
          </p>
        </div>
        {!creating && <Button onClick={() => setCreating(true)}>New Destination</Button>}
      </div>

      {creating && (
        <Card>
          <h2 className="mb-4 text-lg font-semibold text-slate-900">New Destination</h2>
          <DestinationForm
            onSubmit={handleCreate}
            onCancel={() => setCreating(false)}
            submitLabel="Create Destination"
            submittingLabel="Creating..."
          />
        </Card>
      )}

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && destinations.length === 0 && (
        <p className="text-slate-600">No destinations yet. Create the first one above.</p>
      )}

      {!loading && !error && destinations.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Region</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Packages</th>
                <th className="px-4 py-3 text-right">Hotels</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {destinations.map((destination) =>
                editingId === destination.id ? (
                  <tr key={destination.id}>
                    <td colSpan={6} className="bg-slate-50 px-4 py-4">
                      <DestinationForm
                        initialValues={{
                          name: destination.name,
                          region: destination.region,
                          description: destination.description ?? '',
                        }}
                        onSubmit={(values) => handleUpdate(destination.id, values)}
                        onCancel={() => setEditingId(null)}
                        submitLabel="Save Changes"
                        submittingLabel="Saving..."
                      />
                    </td>
                  </tr>
                ) : (
                  <tr key={destination.id} className="text-slate-700">
                    <td className="px-4 py-3 font-medium text-slate-900">{destination.name}</td>
                    <td className="px-4 py-3">{destination.region}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                          destination.status === 'ACTIVE'
                            ? 'bg-green-100 text-green-800'
                            : 'bg-slate-200 text-slate-600'
                        }`}
                      >
                        {destination.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">{destination.activePackageCount ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      {destination.activeAccommodationCount ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="secondary"
                          disabled={busyId === destination.id}
                          onClick={() => setEditingId(destination.id)}
                        >
                          Edit
                        </Button>
                        <Button
                          variant="secondary"
                          disabled={busyId === destination.id}
                          onClick={() => handleToggle(destination)}
                        >
                          {destination.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
