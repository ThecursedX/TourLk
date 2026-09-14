import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import type { ErrorResponse } from '../../types/auth'
import type { RoomRequestDto } from '../../types/accommodation'

interface RoomFormProps {
  initialValues?: RoomRequestDto
  onSubmit: (values: RoomRequestDto) => Promise<void>
  onCancel?: () => void
  submitLabel: string
}

const emptyValues: RoomRequestDto = {
  roomType: '',
  pricePerNight: 0,
  totalRooms: 1,
  maxOccupancy: 1,
}

export default function RoomForm({ initialValues, onSubmit, onCancel, submitLabel }: RoomFormProps) {
  const [values, setValues] = useState<RoomRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.roomType.trim()) {
      errors.roomType = 'Room type is required'
    }
    if (!values.pricePerNight || values.pricePerNight <= 0) {
      errors.pricePerNight = 'Price per night must be greater than 0'
    }
    if (!values.totalRooms || values.totalRooms <= 0) {
      errors.totalRooms = 'Total rooms must be a positive number'
    }
    if (!values.maxOccupancy || values.maxOccupancy <= 0) {
      errors.maxOccupancy = 'Max occupancy must be a positive number'
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
      await onSubmit(values)
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-md border border-slate-200 p-4">
      <Input
        id="roomType"
        label="Room type"
        placeholder="e.g. Deluxe Double"
        value={values.roomType}
        onChange={(e) => setValues((v) => ({ ...v, roomType: e.target.value }))}
        error={fieldErrors.roomType}
      />
      <div className="grid grid-cols-3 gap-3">
        <Input
          id="pricePerNight"
          label="Price / night"
          type="number"
          min={0}
          step="0.01"
          value={values.pricePerNight}
          onChange={(e) => setValues((v) => ({ ...v, pricePerNight: Number(e.target.value) }))}
          error={fieldErrors.pricePerNight}
        />
        <Input
          id="totalRooms"
          label="Total rooms"
          type="number"
          min={1}
          value={values.totalRooms}
          onChange={(e) => setValues((v) => ({ ...v, totalRooms: Number(e.target.value) }))}
          error={fieldErrors.totalRooms}
        />
        <Input
          id="maxOccupancy"
          label="Max occupancy"
          type="number"
          min={1}
          value={values.maxOccupancy}
          onChange={(e) => setValues((v) => ({ ...v, maxOccupancy: Number(e.target.value) }))}
          error={fieldErrors.maxOccupancy}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={submitting}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
