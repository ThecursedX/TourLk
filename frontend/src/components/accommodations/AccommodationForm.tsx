import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import TagInput from '../ui/TagInput'
import ImageUrlListInput, { cleanImageUrls } from '../ui/ImageUrlListInput'
import DestinationSelect from '../destinations/DestinationSelect'
import LocationPicker from '../maps/LocationPicker'
import { getDestinationById } from '../../api/destinationApi'
import type { ErrorResponse } from '../../types/auth'
import { MAX_ACCOMMODATION_IMAGES, type AccommodationRequestDto } from '../../types/accommodation'
import { hasCoordinates, type DestinationSummary } from '../../types/destination'

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
  address: '',
  latitude: null,
  longitude: null,
  facilities: [],
  policies: '',
  imageUrls: [],
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
  // Where the picker opens until a position is chosen: the hotel's destination, when it has coordinates.
  const [destinationCenter, setDestinationCenter] = useState<{ lat: number; lng: number } | null>(
    currentLocation && hasCoordinates(currentLocation)
      ? { lat: currentLocation.latitude as number, lng: currentLocation.longitude as number }
      : null,
  )

  useEffect(() => {
    if (values.locationId === '') {
      setDestinationCenter(null)
      return
    }
    let cancelled = false
    getDestinationById(values.locationId)
      .then((d) => {
        if (!cancelled) setDestinationCenter(hasCoordinates(d) ? { lat: d.latitude as number, lng: d.longitude as number } : null)
      })
      .catch(() => !cancelled && setDestinationCenter(null))
    return () => {
      cancelled = true
    }
  }, [values.locationId])

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
    if ((values.address ?? '').length > 500) {
      errors.address = 'Address must be at most 500 characters'
    }
    if ((values.imageUrls ?? []).length > MAX_ACCOMMODATION_IMAGES) {
      errors.imageUrls = `At most ${MAX_ACCOMMODATION_IMAGES} images are allowed`
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
      await onSubmit({ ...values, imageUrls: cleanImageUrls(values.imageUrls) })
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
      <Input
        id="address"
        label="Address (optional)"
        placeholder="Street, city"
        value={values.address ?? ''}
        onChange={(e) => setValues((v) => ({ ...v, address: e.target.value }))}
        error={fieldErrors.address}
      />
      <div className="flex flex-col gap-2">
        <span className="text-sm font-medium text-slate-700">Map location (optional)</span>
        <LocationPicker
          value={
            values.latitude != null && values.longitude != null
              ? { lat: values.latitude, lng: values.longitude }
              : null
          }
          defaultCenter={destinationCenter}
          onChange={({ lat, lng }) => setValues((v) => ({ ...v, latitude: lat, longitude: lng }))}
        />
        {values.latitude != null && values.longitude != null && (
          <div className="flex items-center gap-3 text-xs text-slate-500">
            <span>
              {values.latitude}, {values.longitude}
            </span>
            <button
              type="button"
              className="font-medium text-blue-600 hover:underline"
              onClick={() => setValues((v) => ({ ...v, latitude: null, longitude: null }))}
            >
              Remove location
            </button>
          </div>
        )}
        {(fieldErrors.latitude || fieldErrors.longitude) && (
          <span className="text-sm text-red-600">{fieldErrors.latitude ?? fieldErrors.longitude}</span>
        )}
      </div>
      <TagInput
        label="Facilities (optional)"
        values={values.facilities ?? []}
        onChange={(next) => setValues((v) => ({ ...v, facilities: next }))}
        placeholder="e.g. Pool, WiFi, then Enter"
        error={fieldErrors.facilities}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor="policies" className="text-sm font-medium text-slate-700">
          Policies (optional)
        </label>
        <textarea
          id="policies"
          rows={3}
          placeholder="Check-in/out times, cancellation, house rules..."
          value={values.policies ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, policies: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      <ImageUrlListInput
        values={values.imageUrls ?? []}
        onChange={(next) => setValues((v) => ({ ...v, imageUrls: next }))}
        max={MAX_ACCOMMODATION_IMAGES}
        error={fieldErrors.imageUrls}
      />
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
