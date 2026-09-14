import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import DestinationSelect from '../destinations/DestinationSelect'
import type { ErrorResponse } from '../../types/auth'
import type { TourPackageRequestDto } from '../../types/tourPackage'
import type { DestinationSummary } from '../../types/destination'

interface PackageFormProps {
  initialValues?: TourPackageRequestDto
  onSubmit: (values: TourPackageRequestDto) => Promise<void>
  submitLabel: string
  submittingLabel: string
  /** The package's saved destination, so an edit form can still show it even if it's now inactive. */
  currentDestination?: DestinationSummary | null
}

const emptyValues: TourPackageRequestDto = {
  title: '',
  description: '',
  destinationId: '',
  durationDays: 1,
  price: 0,
  maxCapacity: 1,
}

export default function PackageForm({
  initialValues,
  onSubmit,
  submitLabel,
  submittingLabel,
  currentDestination,
}: PackageFormProps) {
  const [values, setValues] = useState<TourPackageRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.title.trim()) {
      errors.title = 'Title is required'
    } else if (values.title.length > 200) {
      errors.title = 'Title must be at most 200 characters'
    }
    if (!values.description.trim()) {
      errors.description = 'Description is required'
    }
    if (values.destinationId === '' || !values.destinationId) {
      errors.destinationId = 'Destination is required'
    }
    if (!values.durationDays || values.durationDays <= 0) {
      errors.durationDays = 'Duration must be a positive number of days'
    }
    if (values.price === null || values.price === undefined || values.price <= 0) {
      errors.price = 'Price must be greater than 0'
    }
    if (!values.maxCapacity || values.maxCapacity <= 0) {
      errors.maxCapacity = 'Max capacity must be a positive number'
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
      <Input
        id="title"
        label="Title"
        value={values.title}
        onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))}
        error={fieldErrors.title}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor="description" className="text-sm font-medium text-slate-700">
          Description
        </label>
        <textarea
          id="description"
          rows={4}
          value={values.description}
          onChange={(e) => setValues((v) => ({ ...v, description: e.target.value }))}
          className={`rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            fieldErrors.description ? 'border-red-500' : 'border-slate-300'
          }`}
        />
        {fieldErrors.description && (
          <span className="text-sm text-red-600">{fieldErrors.description}</span>
        )}
      </div>
      <DestinationSelect
        id="destinationId"
        label="Destination"
        value={values.destinationId}
        onChange={(destinationId) => setValues((v) => ({ ...v, destinationId }))}
        error={fieldErrors.destinationId}
        currentOption={currentDestination}
      />
      <div className="grid grid-cols-3 gap-4">
        <Input
          id="durationDays"
          label="Duration (days)"
          type="number"
          min={1}
          value={values.durationDays}
          onChange={(e) => setValues((v) => ({ ...v, durationDays: Number(e.target.value) }))}
          error={fieldErrors.durationDays}
        />
        <Input
          id="price"
          label="Price (USD)"
          type="number"
          min={0}
          step="0.01"
          value={values.price}
          onChange={(e) => setValues((v) => ({ ...v, price: Number(e.target.value) }))}
          error={fieldErrors.price}
        />
        <Input
          id="maxCapacity"
          label="Max capacity"
          type="number"
          min={1}
          value={values.maxCapacity}
          onChange={(e) => setValues((v) => ({ ...v, maxCapacity: Number(e.target.value) }))}
          error={fieldErrors.maxCapacity}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
