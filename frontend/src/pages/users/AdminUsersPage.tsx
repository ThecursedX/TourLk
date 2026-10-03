import { useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import { deactivateUser, deleteUser, getAllUsers, reactivateUser } from '../../api/userApi'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import type { ErrorResponse } from '../../types/auth'
import type { UserResponseDto } from '../../types/user'

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponseDto[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = (query?: string) => {
    setLoading(true)
    setError(null)
    getAllUsers(query)
      .then(setUsers)
      .catch(() => setError('Could not load users. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    load(search.trim() || undefined)
  }

  const describeError = (err: unknown, fallback: string) =>
    isAxiosError<ErrorResponse>(err) && err.response ? err.response.data.message : fallback

  const handleToggle = async (user: UserResponseDto) => {
    setActionError(null)
    setBusyId(user.id)
    try {
      const updated =
        user.status === 'ACTIVE' ? await deactivateUser(user.id) : await reactivateUser(user.id)
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)))
    } catch (err) {
      setActionError(describeError(err, 'That action could not be completed. Please try again.'))
    } finally {
      setBusyId(null)
    }
  }

  const handleDelete = async (user: UserResponseDto) => {
    if (!window.confirm(`Permanently delete ${user.name}'s account? This cannot be undone.`)) {
      return
    }
    setActionError(null)
    setBusyId(user.id)
    try {
      await deleteUser(user.id)
      setUsers((prev) => prev.filter((u) => u.id !== user.id))
    } catch (err) {
      setActionError(describeError(err, 'That user could not be deleted. Please try again.'))
    } finally {
      setBusyId(null)
    }
  }

  const statusClass = (status: UserResponseDto['status']) =>
    status === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-slate-200 text-slate-600'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Manage Users</h1>
        <p className="mt-1 text-slate-600">Search accounts, review roles, and activate or deactivate access.</p>
      </div>

      <form onSubmit={handleSearch} className="flex max-w-sm gap-2">
        <Input
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <Button type="submit">Search</Button>
      </form>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}
      {!loading && !error && users.length === 0 && <p className="text-slate-600">No users found.</p>}

      {!loading && !error && users.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Phone</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((user) => (
                <tr key={user.id} className="text-slate-700">
                  <td className="px-4 py-3 font-medium text-slate-900">{user.name}</td>
                  <td className="px-4 py-3">{user.email}</td>
                  <td className="px-4 py-3">{user.phone ?? '—'}</td>
                  <td className="px-4 py-3">{user.role}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusClass(user.status)}`}>
                      {user.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="secondary"
                        disabled={busyId === user.id}
                        onClick={() => handleToggle(user)}
                      >
                        {user.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                      </Button>
                      {user.status === 'DEACTIVATED' && (
                        <Button
                          variant="secondary"
                          disabled={busyId === user.id}
                          onClick={() => handleDelete(user)}
                        >
                          Delete
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
