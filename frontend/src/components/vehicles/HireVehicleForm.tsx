import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createHire } from '../../api/vehicleHireApi'
import Button from '../ui/Button'
import Input from '../ui/Input'
import type { ErrorResponse } from '../../types/auth'

interface HireVehicleFormProps {
  vehicleId: number
  pricePerDay: number
}

function tomorrow(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return d.toISOString().slice(0, 10)
}

export default function HireVehicleForm({ vehicleId, pricePerDay }: HireVehicleFormProps) {
  const navigate = useNavigate()
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [pickupLocation, setPickupLocation] = useState('')
  const [notes, setNotes] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const days =
    startDate && endDate && endDate >= startDate
      ? Math.round((new Date(endDate).getTime() - new Date(startDate).getTime()) / (1000 * 60 * 60 * 24)) + 1
      : null
  const priceEstimate = days ? days * pricePerDay : null

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!startDate) errors.startDate = 'Start date is required'
    if (!endDate) {
      errors.endDate = 'End date is required'
    } else if (startDate && endDate < startDate) {
      errors.endDate = 'End date must be on or after start date'
    }
    if (!pickupLocation.trim()) errors.pickupLocation = 'Pickup location is required'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await createHire({
        vehicleId,
        startDate,
        endDate,
        pickupLocation,
        notes: notes.trim() || undefined,
      })
      navigate('/hires/mine')
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
          id="startDate"
          label="Start date"
          type="date"
          min={tomorrow()}
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          error={fieldErrors.startDate}
        />
        <Input
          id="endDate"
          label="End date"
          type="date"
          min={startDate || tomorrow()}
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          error={fieldErrors.endDate}
        />
      </div>
      <Input
        id="pickupLocation"
        label="Pickup location"
        placeholder="e.g. Bandaranaike International Airport"
        value={pickupLocation}
        onChange={(e) => setPickupLocation(e.target.value)}
        error={fieldErrors.pickupLocation}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor="notes" className="text-sm font-medium text-slate-700">
          Notes (optional)
        </label>
        <textarea
          id="notes"
          rows={3}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      {priceEstimate !== null && (
        <p className="text-sm text-slate-700">
          {days} day{days === 1 ? '' : 's'} &times;{' '}
          {pricePerDay.toLocaleString(undefined, { style: 'currency', currency: 'USD' })} ={' '}
          <span className="font-semibold text-slate-900">
            {priceEstimate.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
          </span>
        </p>
      )}
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? 'Hiring...' : 'Hire Now'}
      </Button>
    </form>
  )
}
