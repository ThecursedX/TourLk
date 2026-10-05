import { useEffect, useState } from 'react'
import { getPendingLicences, rejectLicence, verifyLicence } from '../../api/userApi'
import RejectReasonForm from '../../components/packages/RejectReasonForm'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import LicenceDocumentLink from '../../components/users/LicenceDocumentLink'
import type { UserResponseDto } from '../../types/user'

export default function AdminLicenceVerificationsPage() {
  const [users, setUsers] = useState<UserResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [rejectingId, setRejectingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getPendingLicences()
      .then(setUsers)
      .catch(() => setError('Could not load pending licence submissions. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const runAction = async (id: number, action: () => Promise<UserResponseDto>) => {
    setActionError(null)
    setBusyId(id)
    try {
      await action()
      setUsers((prev) => prev.filter((u) => u.id !== id))
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
        <h1 className="text-2xl font-semibold text-slate-900">Licence Verifications</h1>
        <p className="mt-1 text-slate-600">Guides and drivers awaiting licence approval.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && users.length === 0 && (
        <p className="text-slate-600">No licences are waiting for review.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {users.map((user) => {
          const disabled = busyId === user.id
          return (
            <Card key={user.id}>
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="font-display text-sm font-bold text-slate-900">{user.name}</span>
                  <span className="rounded-full bg-cobalt-50 px-2 py-0.5 text-xs font-semibold text-cobalt-700">
                    {user.role}
                  </span>
                </div>
                <span className="text-xs text-slate-500">{user.email}</span>
                <dl className="mt-2 flex flex-col gap-1 text-sm text-slate-700">
                  <div className="flex justify-between gap-2">
                    <dt className="text-slate-500">Licence No.</dt>
                    <dd>{user.licenceNumber}</dd>
                  </div>
                  <div className="flex justify-between gap-2">
                    <dt className="text-slate-500">Expiry</dt>
                    <dd>{user.licenceExpiry}</dd>
                  </div>
                  <div>
                    <LicenceDocumentLink user={user} />
                  </div>
                </dl>

                <div className="mt-2">
                  {rejectingId === user.id ? (
                    <RejectReasonForm
                      disabled={disabled}
                      onCancel={() => setRejectingId(null)}
                      onSubmit={(reason) => runAction(user.id, () => rejectLicence(user.id, reason))}
                    />
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <Button disabled={disabled} onClick={() => runAction(user.id, () => verifyLicence(user.id))}>
                        Verify
                      </Button>
                      <Button variant="secondary" disabled={disabled} onClick={() => setRejectingId(user.id)}>
                        Reject
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
