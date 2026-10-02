import { useEffect, useState, type KeyboardEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import { addPackageDeparture, deletePackageDeparture, getPackageDepartures } from '../../api/tourPackageApi'
import { formatLocalDate, tomorrowIso } from '../../utils/date'
import type { ErrorResponse } from '../../types/auth'
import type { PackageDepartureResponseDto } from '../../types/tourPackage'

interface DepartureManagerProps {
  packageId: number
  /** The package's saved max capacity — what the backend uses when seats are left blank. */
  defaultSeats: number
}

function errorMessage(err: unknown, fallback: string): string {
  if (isAxiosError<ErrorResponse>(err) && err.response) {
    const { message, fieldErrors } = err.response.data
    const firstFieldError = fieldErrors ? Object.values(fieldErrors)[0] : undefined
    return firstFieldError ?? message ?? fallback
  }
  return fallback
}

/**
 * Adds/removes a package's departure dates. Each change is saved
 * immediately through its own endpoint, independent of the surrounding
 * package form's Save button — so buttons here are type="button" and
 * there's no nested <form>.
 */
export default function DepartureManager({ packageId, defaultSeats }: DepartureManagerProps) {
  const [departures, setDepartures] = useState<PackageDepartureResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [departureDate, setDepartureDate] = useState('')
  const [seatsTotal, setSeatsTotal] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setLoading(true)
    getPackageDepartures(packageId)
      .then(setDepartures)
      .catch(() => setError('Could not load departures.'))
      .finally(() => setLoading(false))
  }, [packageId])

  const handleAdd = async () => {
    setError(null)
    if (!departureDate) {
      setError('Pick a departure date')
      return
    }
    const seats = seatsTotal.trim() === '' ? undefined : Number(seatsTotal)
    if (seats !== undefined && (!Number.isInteger(seats) || seats <= 0)) {
      setError('Seats must be a positive whole number')
      return
    }

    setBusy(true)
    try {
      const created = await addPackageDeparture(packageId, { departureDate, seatsTotal: seats })
      setDepartures((list) =>
        [...list, created].sort((a, b) => a.departureDate.localeCompare(b.departureDate)),
      )
      setDepartureDate('')
      setSeatsTotal('')
    } catch (err) {
      setError(errorMessage(err, 'Could not add the departure. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  const handleRemove = async (departure: PackageDepartureResponseDto) => {
    setError(null)
    setBusy(true)
    try {
      await deletePackageDeparture(packageId, departure.id)
      setDepartures((list) => list.filter((d) => d.id !== departure.id))
    } catch (err) {
      setError(errorMessage(err, 'Could not remove the departure. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  // Enter would otherwise submit the surrounding package form.
  const addOnEnter = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      void handleAdd()
    }
  }

  return (
    <div className="flex flex-col gap-2 border-t border-slate-200 pt-4">
      <span className="text-sm font-medium text-slate-700">Departures</span>
      <p className="text-sm text-slate-500">
        Once a package has departures, tourists can only book one of these dates. Changes here are saved
        immediately.
      </p>

      {loading ? (
        <p className="text-sm text-slate-500">Loading departures...</p>
      ) : departures.length === 0 ? (
        <p className="text-sm text-slate-500">No upcoming departures scheduled.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {departures.map((departure) => (
            <li
              key={departure.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-4 py-2"
            >
              <div className="flex flex-col">
                <span className="text-sm font-semibold text-slate-900">
                  {formatLocalDate(departure.departureDate)}
                </span>
                <span className="text-xs text-slate-500">
                  {departure.seatsLeft} of {departure.seatsTotal} seats left
                </span>
              </div>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => handleRemove(departure)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-end gap-2">
        <Input
          id="departureDate"
          label="Date"
          type="date"
          min={tomorrowIso()}
          value={departureDate}
          onChange={(e) => setDepartureDate(e.target.value)}
          onKeyDown={addOnEnter}
        />
        <Input
          id="departureSeats"
          label="Seats"
          type="number"
          min={1}
          placeholder={String(defaultSeats)}
          value={seatsTotal}
          onChange={(e) => setSeatsTotal(e.target.value)}
          onKeyDown={addOnEnter}
          className="w-24"
        />
        <Button type="button" variant="secondary" disabled={busy} onClick={handleAdd}>
          Add Departure
        </Button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  )
}
