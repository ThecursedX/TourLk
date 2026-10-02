import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import TagInput from '../ui/TagInput'
import DestinationSelect from '../destinations/DestinationSelect'
import DepartureManager from './DepartureManager'
import type { ErrorResponse } from '../../types/auth'
import type { ItineraryDayRequestDto, TourPackageRequestDto } from '../../types/tourPackage'
import type { DestinationSummary } from '../../types/destination'

interface PackageFormProps {
  initialValues?: TourPackageRequestDto
  onSubmit: (values: TourPackageRequestDto) => Promise<void>
  submitLabel: string
  submittingLabel: string
  /** The package's saved destination, so an edit form can still show it even if it's now inactive. */
  currentDestination?: DestinationSummary | null
  /** Set in edit mode only — departures need a saved package to attach to. */
  packageId?: number
}

const emptyValues: TourPackageRequestDto = {
  title: '',
  description: '',
  destinationId: '',
  durationDays: 1,
  price: 0,
  maxCapacity: 1,
  itineraryDays: [],
  inclusions: [],
  exclusions: [],
  imageUrls: [],
}

const emptyDay: ItineraryDayRequestDto = { title: '', description: '', placesToVisit: [], dayNumber: 1 }

export default function PackageForm({
  initialValues,
  onSubmit,
  submitLabel,
  submittingLabel,
  currentDestination,
  packageId,
}: PackageFormProps) {
  const [values, setValues] = useState<TourPackageRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const itineraryDays = values.itineraryDays ?? []
  const inclusions = values.inclusions ?? []
  const exclusions = values.exclusions ?? []
  const imageUrls = values.imageUrls ?? []

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
    if (itineraryDays.some((day) => !day.title.trim())) {
      errors.itineraryDays = 'Every itinerary day needs a title'
    } else if (values.durationDays && itineraryDays.length > values.durationDays) {
      errors.itineraryDays = `A ${values.durationDays}-day package can have at most ${values.durationDays} itinerary days`
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
        // Days are shown and reordered as a plain list; the day number is
        // just its position, so it's derived here rather than tracked as
        // separate editable state.
        itineraryDays: itineraryDays.map((day, index) => ({ ...day, dayNumber: index + 1 })),
        inclusions,
        exclusions,
        imageUrls: imageUrls.map((url) => url.trim()).filter((url) => url.length > 0),
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

  const updateDay = (index: number, patch: Partial<ItineraryDayRequestDto>) => {
    setValues((v) => {
      const next = [...(v.itineraryDays ?? [])]
      next[index] = { ...next[index], ...patch }
      return { ...v, itineraryDays: next }
    })
  }

  const addDay = () => {
    setValues((v) => ({ ...v, itineraryDays: [...(v.itineraryDays ?? []), { ...emptyDay }] }))
  }

  const removeDay = (index: number) => {
    setValues((v) => ({ ...v, itineraryDays: (v.itineraryDays ?? []).filter((_, i) => i !== index) }))
  }

  const handleImageChange = (index: number, url: string) => {
    setValues((v) => {
      const next = [...(v.imageUrls ?? [])]
      next[index] = url
      return { ...v, imageUrls: next }
    })
  }

  const handleImageAdd = () => {
    setValues((v) => ({ ...v, imageUrls: [...(v.imageUrls ?? []), ''] }))
  }

  const handleImageRemove = (index: number) => {
    setValues((v) => ({ ...v, imageUrls: (v.imageUrls ?? []).filter((_, i) => i !== index) }))
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

      <div className="flex flex-col gap-2 border-t border-slate-200 pt-4">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-slate-700">Itinerary (optional)</span>
          <Button type="button" variant="secondary" onClick={addDay}>
            Add Day
          </Button>
        </div>
        {fieldErrors.itineraryDays && <span className="text-sm text-red-600">{fieldErrors.itineraryDays}</span>}
        {itineraryDays.length === 0 && (
          <p className="text-sm text-slate-500">No day-by-day itinerary added yet.</p>
        )}
        {itineraryDays.map((day, index) => (
          <div key={index} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-slate-900">Day {index + 1}</span>
              <Button type="button" variant="secondary" onClick={() => removeDay(index)}>
                Remove Day
              </Button>
            </div>
            <Input
              placeholder="Day title, e.g. Arrival & city tour"
              value={day.title}
              onChange={(e) => updateDay(index, { title: e.target.value })}
            />
            <textarea
              placeholder="Day description (optional)"
              rows={2}
              value={day.description ?? ''}
              onChange={(e) => updateDay(index, { description: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <TagInput
              label="Places to visit"
              values={day.placesToVisit ?? []}
              onChange={(places) => updateDay(index, { placesToVisit: places })}
              placeholder="Type a place and press Enter"
            />
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-2">
        <TagInput
          label="Inclusions (optional)"
          values={inclusions}
          onChange={(next) => setValues((v) => ({ ...v, inclusions: next }))}
          placeholder="e.g. All meals, then Enter"
        />
        <TagInput
          label="Exclusions (optional)"
          values={exclusions}
          onChange={(next) => setValues((v) => ({ ...v, exclusions: next }))}
          placeholder="e.g. International flights, then Enter"
        />
      </div>

      <div className="flex flex-col gap-2 border-t border-slate-200 pt-4">
        <span className="text-sm font-medium text-slate-700">Images (optional)</span>
        {imageUrls.map((url, index) => (
          <div key={index} className="flex items-center gap-2">
            {url.trim() && (
              <img
                src={url}
                alt=""
                className="h-12 w-12 shrink-0 rounded-lg border border-slate-200 object-cover"
                onError={(e) => {
                  e.currentTarget.style.visibility = 'hidden'
                }}
              />
            )}
            <Input
              placeholder="https://..."
              value={url}
              onChange={(e) => handleImageChange(index, e.target.value)}
              className="flex-1"
            />
            <Button type="button" variant="secondary" onClick={() => handleImageRemove(index)}>
              Remove
            </Button>
          </div>
        ))}
        <div>
          <Button type="button" variant="secondary" onClick={handleImageAdd} disabled={imageUrls.length >= 10}>
            Add Image URL
          </Button>
        </div>
      </div>

      {packageId !== undefined && (
        <DepartureManager packageId={packageId} defaultSeats={initialValues?.maxCapacity ?? values.maxCapacity} />
      )}

      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? submittingLabel : submitLabel}
      </Button>
    </form>
  )
}
