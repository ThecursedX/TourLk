import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import DestinationSelect from '../destinations/DestinationSelect'
import type { ErrorResponse } from '../../types/auth'
import type { AccommodationRequestDto } from '../../types/accommodation'
import type { DestinationSummary } from '../../types/destination'

interface AccommodationFormProps {
  initialValues?: AccommodationRequestDto
  onSubmit: (values: AccommodationRequestDto) => Promise<void>
  submitLabel: string
  submittingLabel: string
  /** The property's saved location, so an edit form can still show it even if it's now inactive. */
  currentLocation?: DestinationSummary | null
}

const emptyValues: AccommodationRequestDto = {
  name: '',
  description: '',
  locationId: '',
  starRating: undefined,
}

export default function AccommodationForm({
  initialValues,
  onSubmit,
  submitLabel,
  submittingLabel,
  currentLocation,
}: AccommodationFormProps) {
  const [values, setValues] = useState<AccommodationRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 200) {
      errors.name = 'Name must be at most 200 characters'
    }
    if (!values.description.trim()) {
      errors.description = 'Description is required'
    }
    if (values.locationId === '' || !values.locationId) {
      errors.locationId = 'Location is required'
    }
    if (values.starRating !== undefined && (values.starRating < 1 || values.starRating > 5)) {
      errors.starRating = 'Star rating must be between 1 and 5'
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
        id="name"
        label="Property name"
        value={values.name}
        onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
        error={fieldErrors.name}
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
      <div className="grid grid-cols-2 gap-4">
        <DestinationSelect
          id="locationId"
          label="Location"
          value={values.locationId}
          onChange={(locationId) => setValues((v) => ({ ...v, locationId }))}
          error={fieldErrors.locationId}
          currentOption={currentLocation}
        />
        <Input
          id="starRating"
          label="Star rating (optional)"
          type="number"
          min={1}
          max={5}
          value={values.starRating ?? ''}
          onChange={(e) =>
            setValues((v) => ({
              ...v,
              starRating: e.target.value === '' ? undefined : Number(e.target.value),
            }))
          }
          error={fieldErrors.starRating}
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
