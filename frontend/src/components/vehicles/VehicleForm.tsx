import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import Select from '../ui/Select'
import type { ErrorResponse } from '../../types/auth'
import { VEHICLE_TYPES, type VehicleRequestDto } from '../../types/vehicle'

interface VehicleFormProps {
  initialValues?: VehicleRequestDto
  onSubmit: (values: VehicleRequestDto) => Promise<void>
  submitLabel: string
  submittingLabel: string
}

const emptyValues: VehicleRequestDto = {
  vehicleType: 'CAR',
  make: '',
  model: '',
  registrationNumber: '',
  seatingCapacity: 1,
  pricePerDay: 0,
}

export default function VehicleForm({
  initialValues,
  onSubmit,
  submitLabel,
  submittingLabel,
}: VehicleFormProps) {
  const [values, setValues] = useState<VehicleRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.make.trim()) errors.make = 'Make is required'
    if (!values.model.trim()) errors.model = 'Model is required'
    if (!values.registrationNumber.trim()) errors.registrationNumber = 'Registration number is required'
    if (!values.seatingCapacity || values.seatingCapacity <= 0) {
      errors.seatingCapacity = 'Seating capacity must be a positive number'
    }
    if (!values.pricePerDay || values.pricePerDay <= 0) {
      errors.pricePerDay = 'Price per day must be greater than 0'
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      <Select
        id="vehicleType"
        label="Vehicle type"
        value={values.vehicleType}
        onChange={(e) => setValues((v) => ({ ...v, vehicleType: e.target.value as typeof v.vehicleType }))}
        error={fieldErrors.vehicleType}
      >
        {VEHICLE_TYPES.map((type) => (
          <option key={type} value={type}>
            {type}
          </option>
        ))}
      </Select>
      <div className="grid grid-cols-2 gap-4">
        <Input
          id="make"
          label="Make"
          placeholder="e.g. Toyota"
          value={values.make}
          onChange={(e) => setValues((v) => ({ ...v, make: e.target.value }))}
          error={fieldErrors.make}
        />
        <Input
          id="model"
          label="Model"
          placeholder="e.g. Hiace"
          value={values.model}
          onChange={(e) => setValues((v) => ({ ...v, model: e.target.value }))}
          error={fieldErrors.model}
        />
      </div>
      <Input
        id="registrationNumber"
        label="Registration number"
        value={values.registrationNumber}
        onChange={(e) => setValues((v) => ({ ...v, registrationNumber: e.target.value }))}
        error={fieldErrors.registrationNumber}
      />
      <div className="grid grid-cols-2 gap-4">
        <Input
          id="seatingCapacity"
          label="Seating capacity"
          type="number"
          min={1}
          value={values.seatingCapacity}
          onChange={(e) => setValues((v) => ({ ...v, seatingCapacity: Number(e.target.value) }))}
          error={fieldErrors.seatingCapacity}
        />
        <Input
          id="pricePerDay"
          label="Price per day (USD)"
          type="number"
          min={0}
          step="0.01"
          value={values.pricePerDay}
          onChange={(e) => setValues((v) => ({ ...v, pricePerDay: Number(e.target.value) }))}
          error={fieldErrors.pricePerDay}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
