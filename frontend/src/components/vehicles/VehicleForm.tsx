import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import Select from '../ui/Select'
import TagInput from '../ui/TagInput'
import ImageUrlListInput, { cleanImageUrls } from '../ui/ImageUrlListInput'
import type { ErrorResponse } from '../../types/auth'
import { MAX_VEHICLE_IMAGES, VEHICLE_TYPES, type VehicleRequestDto } from '../../types/vehicle'

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
  airConditioned: false,
  facilities: [],
  imageUrls: [],
  driverName: '',
  driverPhone: '',
  insuranceExpiry: '',
  lastMaintenanceDate: '',
  nextMaintenanceDate: '',
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
    if (
      values.lastMaintenanceDate &&
      values.nextMaintenanceDate &&
      values.nextMaintenanceDate < values.lastMaintenanceDate
    ) {
      errors.nextMaintenanceDate = 'Next maintenance must be on or after the last maintenance date'
    }
    if ((values.imageUrls ?? []).length > MAX_VEHICLE_IMAGES) {
      errors.imageUrls = `At most ${MAX_VEHICLE_IMAGES} images are allowed`
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
      await onSubmit({
        ...values,
        imageUrls: cleanImageUrls(values.imageUrls),
        // Empty date inputs must be omitted, not sent as '' (the API expects a date or null).
        insuranceExpiry: values.insuranceExpiry || undefined,
        lastMaintenanceDate: values.lastMaintenanceDate || undefined,
        nextMaintenanceDate: values.nextMaintenanceDate || undefined,
      })
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
      <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
        <input
          type="checkbox"
          checked={values.airConditioned ?? false}
          onChange={(e) => setValues((v) => ({ ...v, airConditioned: e.target.checked }))}
        />
        Air conditioned
      </label>
      <TagInput
        label="Facilities (optional)"
        values={values.facilities ?? []}
        onChange={(next) => setValues((v) => ({ ...v, facilities: next }))}
        placeholder="e.g. WiFi, USB charging, then Enter"
        error={fieldErrors.facilities}
      />
      <ImageUrlListInput
        values={values.imageUrls ?? []}
        onChange={(next) => setValues((v) => ({ ...v, imageUrls: next }))}
        max={MAX_VEHICLE_IMAGES}
        error={fieldErrors.imageUrls}
      />
      <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4">
        <Input
          id="driverName"
          label="Driver name (optional)"
          placeholder="Defaults to the owner"
          value={values.driverName ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, driverName: e.target.value }))}
          error={fieldErrors.driverName}
        />
        <Input
          id="driverPhone"
          label="Driver phone (optional)"
          type="tel"
          value={values.driverPhone ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, driverPhone: e.target.value }))}
          error={fieldErrors.driverPhone}
        />
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Input
          id="insuranceExpiry"
          label="Insurance expiry"
          type="date"
          value={values.insuranceExpiry ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, insuranceExpiry: e.target.value }))}
          error={fieldErrors.insuranceExpiry}
        />
        <Input
          id="lastMaintenanceDate"
          label="Last maintenance"
          type="date"
          value={values.lastMaintenanceDate ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, lastMaintenanceDate: e.target.value }))}
          error={fieldErrors.lastMaintenanceDate}
        />
        <Input
          id="nextMaintenanceDate"
          label="Next maintenance"
          type="date"
          value={values.nextMaintenanceDate ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, nextMaintenanceDate: e.target.value }))}
          error={fieldErrors.nextMaintenanceDate}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
