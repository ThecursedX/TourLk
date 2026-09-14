import { useEffect, useState } from 'react'
import { approvePackage, getPendingApprovalPackages, rejectPackage } from '../../api/tourPackageApi'
import PackageCard from '../../components/packages/PackageCard'
import Button from '../../components/ui/Button'
import type { TourPackageResponseDto } from '../../types/tourPackage'

export default function AdminApprovalsPage() {
  const [packages, setPackages] = useState<TourPackageResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getPendingApprovalPackages()
      .then(setPackages)
      .catch(() => setError('Could not load pending packages. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleDecision = async (id: number, decision: (id: number) => Promise<TourPackageResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      await decision(id)
      setPackages((prev) => prev.filter((p) => p.id !== id))
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Admin Approvals</h1>
        <p className="mt-1 text-slate-600">Tour packages awaiting approval.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && packages.length === 0 && (
        <p className="text-slate-600">No packages are waiting for approval.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packages.map((pkg) => {
          const disabled = busyId === pkg.id
          return (
            <PackageCard
              key={pkg.id}
              tourPackage={pkg}
              footer={
                <div className="flex gap-2">
                  <Button disabled={disabled} onClick={() => handleDecision(pkg.id, approvePackage)}>
                    Approve
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={disabled}
                    onClick={() => handleDecision(pkg.id, rejectPackage)}
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
