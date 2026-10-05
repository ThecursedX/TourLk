import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createReservation } from '../../api/roomReservationApi'
import Button from '../ui/Button'
import Input from '../ui/Input'
import type { ErrorResponse } from '../../types/auth'
import { tomorrowIso } from '../../utils/date'
import { formatErrorResponse } from '../../utils/errors'

interface ReserveRoomFormProps {
  roomId: number
  onClose: () => void
}

export default function ReserveRoomForm({ roomId, onClose }: ReserveRoomFormProps) {
  const navigate = useNavigate()
  const [checkInDate, setCheckInDate] = useState('')
  const [checkOutDate, setCheckOutDate] = useState('')
  const [numberOfRooms, setNumberOfRooms] = useState(1)
  const [numberOfGuests, setNumberOfGuests] = useState(1)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!checkInDate) errors.checkInDate = 'Check-in date is required'
    if (!checkOutDate) {
      errors.checkOutDate = 'Check-out date is required'
    } else if (checkInDate && checkOutDate <= checkInDate) {
      errors.checkOutDate = 'Check-out date must be after check-in date'
    }
    if (!numberOfRooms || numberOfRooms <= 0) {
      errors.numberOfRooms = 'Number of rooms must be a positive number'
    }
    if (!numberOfGuests || numberOfGuests <= 0) {
      errors.numberOfGuests = 'Number of guests must be a positive number'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await createReservation({ roomId, checkInDate, checkOutDate, numberOfRooms, numberOfGuests })
      navigate('/reservations/mine')
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(formatErrorResponse(err.response.data))
        setFieldErrors(err.response.data.fieldErrors ?? {})
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-soft" noValidate>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Input
          id={`checkIn-${roomId}`}
          label="Check-in"
          type="date"
          min={tomorrowIso()}
          value={checkInDate}
          onChange={(e) => setCheckInDate(e.target.value)}
          error={fieldErrors.checkInDate}
        />
        <Input
          id={`checkOut-${roomId}`}
          label="Check-out"
          type="date"
          min={checkInDate || tomorrowIso()}
          value={checkOutDate}
          onChange={(e) => setCheckOutDate(e.target.value)}
          error={fieldErrors.checkOutDate}
        />
        <Input
          id={`rooms-${roomId}`}
          label="Rooms"
          type="number"
          min={1}
          value={numberOfRooms}
          onChange={(e) => setNumberOfRooms(Number(e.target.value))}
          error={fieldErrors.numberOfRooms}
        />
        <Input
          id={`guests-${roomId}`}
          label="Guests"
          type="number"
          min={1}
          value={numberOfGuests}
          onChange={(e) => setNumberOfGuests(Number(e.target.value))}
          error={fieldErrors.numberOfGuests}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Reserving...' : 'Confirm Reservation'}
        </Button>
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
