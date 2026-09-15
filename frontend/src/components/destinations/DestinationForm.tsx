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

// 1. ලංකාවේ පළාත් ටික මෙතනින් හදාගන්න
const sriLankanProvinces = [
  "Central Province",
  "Eastern Province",
  "North Central Province",
  "Northern Province",
  "North Western Province",
  "Sabaragamuwa Province",
  "Southern Province",
  "Uva Province",
  "Western Province"
]

const emptyValues: DestinationRequestDto = {
  name: '',
  region: '',
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

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 150) {
      errors.name = 'Name must be at most 150 characters'
    }
    if (!values.region.trim()) {
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
      await onSubmit({ ...values, description: values.description?.trim() || undefined })
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
          <Input
              id="name"
              label="Name"
              value={values.name}
              onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
              error={fieldErrors.name}
          />

          {/* 2. පරණ Region Input එක වෙනුවට මේ Dropdown එක දැම්මා */}
          <div className="flex flex-col gap-1">
            <label htmlFor="region" className="text-sm font-medium text-slate-700">
              Region (Province)
            </label>
            <select
                id="region"
                value={values.region}
                onChange={(e) => setValues((v) => ({ ...v, region: e.target.value }))}
                className={`rounded-md border px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                    fieldErrors.region ? 'border-red-500' : 'border-slate-300'
                }`}
            >
              <option value="" disabled>Select a province</option>
              {sriLankanProvinces.map((province, index) => (
                  <option key={index} value={province}>
                    {province}
                  </option>
              ))}
            </select>
            {fieldErrors.region && (
                <span className="text-sm text-red-600">{fieldErrors.region}</span>
            )}
          </div>
        </div>

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

        <div className="flex gap-2">
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