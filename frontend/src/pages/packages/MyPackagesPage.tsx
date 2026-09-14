import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  archivePackage,
  deactivatePackage,
  getMyPackages,
  reactivatePackage,
  submitPackageForApproval,
} from '../../api/tourPackageApi'
import PackageCard from '../../components/packages/PackageCard'
import Button from '../../components/ui/Button'
import type { TourPackageResponseDto } from '../../types/tourPackage'

export default function MyPackagesPage() {
  const [packages, setPackages] = useState<TourPackageResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getMyPackages()
      .then(setPackages)
      .catch(() => setError('Could not load your packages. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const runAction = async (id: number, action: (id: number) => Promise<TourPackageResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action(id)
      setPackages((prev) => prev.map((p) => (p.id === id ? updated : p)))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">My Packages</h1>
          <p className="mt-1 text-slate-600">Manage the tour packages you've created.</p>
        </div>
        <Link to="/packages/new">
          <Button>New Package</Button>
        </Link>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && packages.length === 0 && (
        <p className="text-slate-600">You haven't created any tour packages yet.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packages.map((pkg) => {
          const disabled = busyId === pkg.id
          return (
            <PackageCard
              key={pkg.id}
              tourPackage={pkg}
              footer={
                <div className="flex flex-wrap gap-2">
                  {(pkg.status === 'DRAFT' || pkg.status === 'ACTIVE') && (
                    <Link to={`/packages/${pkg.id}/edit`}>
                      <Button variant="secondary" disabled={disabled}>
                        Edit
                      </Button>
                    </Link>
                  )}
                  {pkg.status === 'DRAFT' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(pkg.id, submitPackageForApproval)}
                    >
                      Submit
                    </Button>
                  )}
                  {pkg.status === 'ACTIVE' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(pkg.id, deactivatePackage)}
                    >
                      Deactivate
                    </Button>
                  )}
                  {pkg.status === 'INACTIVE' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(pkg.id, reactivatePackage)}
                    >
                      Reactivate
                    </Button>
                  )}
                  {pkg.status !== 'ARCHIVED' && (
                    <Button
                      variant="secondary"
                      disabled={disabled}
                      onClick={() => runAction(pkg.id, archivePackage)}
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
