import { useState, type FormEvent } from 'react'
import Button from '../ui/Button'

interface RejectReasonFormProps {
  onSubmit: (reason: string) => Promise<void>
  onCancel: () => void
  disabled?: boolean
}

/** Inline "why are you rejecting this?" form — the reason is required and shown to the package's owner. */
export default function RejectReasonForm({ onSubmit, onCancel, disabled }: RejectReasonFormProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!reason.trim()) {
      setError('Please give the guide a reason')
      return
    }
    if (reason.length > 1000) {
      setError('Reason must be at most 1000 characters')
      return
    }
    setError(null)
    await onSubmit(reason.trim())
  }

  return (
    <form onSubmit={handleSubmit} className="flex w-full flex-col gap-2" noValidate>
      <label htmlFor="reject-reason" className="text-sm font-medium text-slate-700">
        Reason for rejection
      </label>
      <textarea
        id="reject-reason"
        rows={3}
        autoFocus
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="e.g. Please add real photos of the itinerary stops"
        className={`rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
          error ? 'border-red-500' : 'border-slate-300'
        }`}
      />
      {error && <span className="text-sm text-red-600">{error}</span>}
      <div className="flex gap-2">
        <Button type="submit" disabled={disabled}>
          Reject
        </Button>
        <Button type="button" variant="secondary" disabled={disabled} onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
