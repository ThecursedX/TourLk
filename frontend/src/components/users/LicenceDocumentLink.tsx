import { useState } from 'react'
import { openLicenceDocument } from '../../api/userApi'
import Button from '../ui/Button'
import type { UserResponseDto } from '../../types/user'

/**
 * "View document" for a user's licence: the uploaded file is fetched with the JWT and opened in a new
 * tab; legacy submissions that only have a URL fall back to a plain link.
 */
export default function LicenceDocumentLink({ user, asButton = false }: { user: UserResponseDto; asButton?: boolean }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!user.licenceDocumentUploaded) {
    if (!user.licenceDocumentUrl) {
      return <span className="text-sm text-slate-500">No document</span>
    }
    return (
      <a href={user.licenceDocumentUrl} target="_blank" rel="noreferrer" className="text-sm text-cobalt-700 underline">
        View document
      </a>
    )
  }

  const open = async () => {
    setError(null)
    setLoading(true)
    try {
      await openLicenceDocument(user.id)
    } catch {
      setError('Could not open the document.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <span className="inline-flex flex-col items-start gap-1">
      {asButton ? (
        <Button type="button" variant="secondary" disabled={loading} onClick={open}>
          {loading ? 'Opening...' : 'View document'}
        </Button>
      ) : (
        <button type="button" disabled={loading} onClick={open} className="text-sm text-cobalt-700 underline">
          {loading ? 'Opening...' : 'View document'}
        </button>
      )}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </span>
  )
}
