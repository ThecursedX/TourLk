import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { approvePackage, getAdminPackages, rejectPackage } from '../../api/tourPackageApi'
import PackageCard from '../../components/packages/PackageCard'
import RejectReasonForm from '../../components/packages/RejectReasonForm'
import Button from '../../components/ui/Button'
import type { PackageStatus, TourPackageResponseDto } from '../../types/tourPackage'

const STATUS_TABS: { label: string; value: PackageStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Pending Approval', value: 'PENDING_APPROVAL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Archived', value: 'ARCHIVED' },
]

function tabFromUrl(value: string | null): PackageStatus | undefined {
  return STATUS_TABS.find((tab) => tab.value === value)?.value
}

/**
 * Every package on the platform, by status. The selected tab lives in
 * ?status= so the "package submitted" notification can link straight to
 * the Pending Approval tab. Pending packages can be approved or rejected
 * (with a required reason) right here.
 */
export default function AdminPackagesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const statusFilter = tabFromUrl(searchParams.get('status'))

  const [packages, setPackages] = useState<TourPackageResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [rejectingId, setRejectingId] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setRejectingId(null)
    getAdminPackages(statusFilter)
      .then((data) => {
        if (!cancelled) setPackages(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load packages. Please try again later.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [statusFilter])

  const selectTab = (status: PackageStatus | undefined) => {
    setSearchParams(status ? { status } : {})
  }

  /** Replaces the package in place, or drops it if it no longer belongs on the current tab. */
  const applyUpdate = (updated: TourPackageResponseDto) => {
    setPackages((prev) =>
      statusFilter && updated.status !== statusFilter
        ? prev.filter((p) => p.id !== updated.id)
        : prev.map((p) => (p.id === updated.id ? updated : p)),
    )
  }

  const runAction = async (id: number, action: () => Promise<TourPackageResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      applyUpdate(await action())
      setRejectingId(null)
    } catch {
      setActionError('That action could not be completed. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Tour Packages</h1>
        <p className="mt-1 text-slate-600">Every tour package on the platform, by status.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => (
          <Button
            key={tab.label}
            variant={statusFilter === tab.value ? 'primary' : 'secondary'}
            onClick={() => selectTab(tab.value)}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && packages.length === 0 && (
        <p className="text-slate-600">
          {statusFilter === 'PENDING_APPROVAL' ? 'No packages are waiting for approval.' : 'No packages found.'}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {packages.map((pkg) => {
          const disabled = busyId === pkg.id
          let footer = null
          if (pkg.status === 'PENDING_APPROVAL') {
            footer =
              rejectingId === pkg.id ? (
                <RejectReasonForm
                  disabled={disabled}
                  onCancel={() => setRejectingId(null)}
                  onSubmit={(reason) => runAction(pkg.id, () => rejectPackage(pkg.id, reason))}
                />
              ) : (
                <div className="flex gap-2">
                  <Button disabled={disabled} onClick={() => runAction(pkg.id, () => approvePackage(pkg.id))}>
                    Approve
                  </Button>
                  <Button variant="secondary" disabled={disabled} onClick={() => setRejectingId(pkg.id)}>
                    Reject
                  </Button>
                </div>
              )
          }
          return (
            <PackageCard
              key={pkg.id}
              tourPackage={pkg}
              footer={
                <div className="flex w-full flex-col gap-2">
                  <span className="text-xs text-slate-500">By {pkg.createdByName}</span>
                  {footer}
                </div>
              }
            />
          )
        })}
      </div>
    </div>
  )
}
