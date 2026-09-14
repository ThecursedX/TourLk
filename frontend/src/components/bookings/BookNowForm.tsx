import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createBooking } from '../../api/bookingApi'
import Button from '../ui/Button'
import Input from '../ui/Input'
import type { ErrorResponse } from '../../types/auth'

interface BookNowFormProps {
  tourPackageId: number
}

function tomorrow(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return d.toISOString().slice(0, 10)
}

export default function BookNowForm({ tourPackageId }: BookNowFormProps) {
  const navigate = useNavigate()
  const [travelDate, setTravelDate] = useState('')
  const [numberOfTravelers, setNumberOfTravelers] = useState(1)
  const [specialRequests, setSpecialRequests] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!travelDate) {
      errors.travelDate = 'Travel date is required'
    }
    if (!numberOfTravelers || numberOfTravelers <= 0) {
      errors.numberOfTravelers = 'Number of travelers must be a positive number'
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
      await createBooking({
        tourPackageId,
        travelDate,
        numberOfTravelers,
        specialRequests: specialRequests.trim() || undefined,
      })
      navigate('/bookings/mine')
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(err.response.data.message)
        setFieldErrors(err.response.data.fieldErrors ?? {})
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      <div className="grid grid-cols-2 gap-4">
        <Input
          id="travelDate"
          label="Travel date"
          type="date"
          min={tomorrow()}
          value={travelDate}
          onChange={(e) => setTravelDate(e.target.value)}
          error={fieldErrors.travelDate}
        />
        <Input
          id="numberOfTravelers"
          label="Number of travelers"
          type="number"
          min={1}
          value={numberOfTravelers}
          onChange={(e) => setNumberOfTravelers(Number(e.target.value))}
          error={fieldErrors.numberOfTravelers}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="specialRequests" className="text-sm font-medium text-slate-700">
          Special requests (optional)
        </label>
        <textarea
          id="specialRequests"
          rows={3}
          value={specialRequests}
          onChange={(e) => setSpecialRequests(e.target.value)}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? 'Booking...' : 'Book Now'}
      </Button>
    </form>
  )
}
