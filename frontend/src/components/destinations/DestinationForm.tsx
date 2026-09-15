import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../ui/Button'
import Input from '../ui/Input'
import type { ErrorResponse } from '../../types/auth'
import type { DestinationRequestDto } from '../../types/destination'

interface DestinationFormProps {
  initialValues?: DestinationRequestDto
  onSubmit: (values: DestinationRequestDto) => Promise<void>
  onCancel?: () => void
  submitLabel: string
  submittingLabel: string
}

// ලංකාවේ පළාත් සහ ඒවාට අදාළ දිස්ත්‍රික්ක ලැයිස්තුව
const sriLankaData: Record<string, string[]> = {
  "Western Province": ["Colombo", "Gampaha", "Kalutara"],
  "Central Province": ["Kandy", "Matale", "Nuwara Eliya"],
  "Southern Province": ["Galle", "Matara", "Hambantota"],
  "Northern Province": ["Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu"],
  "Eastern Province": ["Trincomalee", "Batticaloa", "Ampara"],
  "North Western Province": ["Kurunegala", "Puttalam"],
  "North Central Province": ["Anuradhapura", "Polonnaruwa"],
  "Uva Province": ["Badulla", "Monaragala"],
  "Sabaragamuwa Province": ["Ratnapura", "Kegalle"]
};

// ප්‍රධාන කාණ්ඩ (Categories)
const categories = [
  "Beach",
  "Historical",
  "Waterfall",
  "Wildlife",
  "Mountain / Hiking",
  "Religious"
];

const emptyValues: DestinationRequestDto = {
  name: '',
  region: '',
  district: '',
  category: '',
  bestTimeToVisit: '',
  imageUrl: '',
  description: '',
}

export default function DestinationForm({
                                          initialValues,
                                          onSubmit,
                                          onCancel,
                                          submitLabel,
                                          submittingLabel,
                                        }: DestinationFormProps) {
  const [values, setValues] = useState<DestinationRequestDto>(initialValues ?? emptyValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // තෝරපු Province එකට අදාළ Districts ටික ලබා ගැනීම
  const availableDistricts = values.region ? sriLankaData[values.region] || [] : [];

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name?.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 150) {
      errors.name = 'Name must be at most 150 characters'
    }
    if (!values.region?.trim()) {
      errors.region = 'Region is required'
    }
    if (values.description && values.description.length > 2000) {
      errors.description = 'Description must be at most 2000 characters'
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
        description: values.description?.trim() || undefined,
        district: values.district?.trim() || undefined,
        category: values.category?.trim() || undefined,
        bestTimeToVisit: values.bestTimeToVisit?.trim() || undefined,
        imageUrl: values.imageUrl?.trim() || undefined,
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
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Name */}
          <Input
              id="name"
              label="Name"
              value={values.name}
              onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
              error={fieldErrors.name}
          />

          {/* Region (Province) - Dropdown / Custom Type */}
          <div className="flex flex-col gap-1">
            <label htmlFor="region" className="text-sm font-medium text-slate-700">
              Region (Province)
            </label>
            <input
                list="provinces-list"
                id="region"
                value={values.region}
                onChange={(e) => setValues((v) => ({ ...v, region: e.target.value, district: '' }))}
                placeholder="Select or type province"
                className={`rounded-md border px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                    fieldErrors.region ? 'border-red-500' : 'border-slate-300'
                }`}
            />
            <datalist id="provinces-list">
              {Object.keys(sriLankaData).map((province) => (
                  <option key={province} value={province} />
              ))}
            </datalist>
            {fieldErrors.region && (
                <span className="text-sm text-red-600">{fieldErrors.region}</span>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* District - Dropdown / Custom Type */}
          <div className="flex flex-col gap-1">
            <label htmlFor="district" className="text-sm font-medium text-slate-700">
              District
            </label>
            <input
                list="districts-list"
                id="district"
                value={values.district ?? ''}
                onChange={(e) => setValues((v) => ({ ...v, district: e.target.value }))}
                placeholder={values.region ? "Select or type district" : "Select a province first"}
                disabled={!values.region}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-slate-100"
            />
            <datalist id="districts-list">
              {availableDistricts.map((district) => (
                  <option key={district} value={district} />
              ))}
            </datalist>
          </div>

          {/* Category - Dropdown / Custom Type */}
          <div className="flex flex-col gap-1">
            <label htmlFor="category" className="text-sm font-medium text-slate-700">
              Category
            </label>
            <input
                list="categories-list"
                id="category"
                value={values.category ?? ''}
                onChange={(e) => setValues((v) => ({ ...v, category: e.target.value }))}
                placeholder="Select or type category"
                className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <datalist id="categories-list">
              {categories.map((cat) => (
                  <option key={cat} value={cat} />
              ))}
            </datalist>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Best Time to Visit */}
          <Input
              id="bestTimeToVisit"
              label="Best Time to Visit (optional)"
              placeholder="e.g. January to April"
              value={values.bestTimeToVisit ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, bestTimeToVisit: e.target.value }))}
          />

          {/* Image URL */}
          <Input
              id="imageUrl"
              label="Image URL (optional)"
              placeholder="https://example.com/image.jpg"
              value={values.imageUrl ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, imageUrl: e.target.value }))}
          />
        </div>

        {/* Description */}
        <div className="flex flex-col gap-1">
          <label htmlFor="description" className="text-sm font-medium text-slate-700">
            Description (optional)
          </label>
          <textarea
              id="description"
              rows={3}
              value={values.description ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, description: e.target.value }))}
              className={`rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                  fieldErrors.description ? 'border-red-500' : 'border-slate-300'
              }`}
          />
          {fieldErrors.description && (
              <span className="text-sm text-red-600">{fieldErrors.description}</span>
          )}
        </div>

        {formError && <p className="text-sm text-red-600">{formError}</p>}

        <div className="grid grid-cols-1 gap-2 sm:flex sm:gap-2">
          <Button type="submit" disabled={submitting}>
            {submitting ? submittingLabel : submitLabel}
          </Button>
          {onCancel && (
              <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>
                Cancel
              </Button>
          )}
        </div>
      </form>
  )
}