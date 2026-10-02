import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { getCategorySuggestions } from '../../api/destinationApi'
import { getProvinceDistricts } from '../../api/referenceDataApi'
import Button from '../ui/Button'
import Combobox from '../ui/Combobox'
import Input from '../ui/Input'
import Select from '../ui/Select'
import type { ErrorResponse } from '../../types/auth'
import {
  PROVINCES,
  formatProvince,
  type DestinationRequestDto,
  type ProvinceDistrictMap,
} from '../../types/destination'

interface DestinationFormProps {
  initialValues?: DestinationRequestDto
  onSubmit: (values: DestinationRequestDto) => Promise<void>
  onCancel?: () => void
  submitLabel: string
  submittingLabel: string
}

const emptyValues: DestinationRequestDto = {
  name: '',
  description: '',
  province: '',
  district: '',
  category: '',
  bestTimeToVisit: '',
  imageUrls: [],
  openingHours: '',
  entryFee: null,
  visitorRules: '',
  latitude: null,
  longitude: null,
  saveAsDraft: false,
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

  const [provinceDistricts, setProvinceDistricts] = useState<ProvinceDistrictMap | null>(null)
  const [categoryOptions, setCategoryOptions] = useState<string[]>([])

  useEffect(() => {
    getProvinceDistricts().then(setProvinceDistricts).catch(() => setProvinceDistricts(null))
    getCategorySuggestions().then(setCategoryOptions).catch(() => setCategoryOptions([]))
  }, [])

  const districtOptions = values.province && provinceDistricts ? provinceDistricts[values.province] ?? [] : []

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 150) {
      errors.name = 'Name must be at most 150 characters'
    }
    if (values.description && values.description.length > 2000) {
      errors.description = 'Description must be at most 2000 characters'
    }
    if (!values.province) {
      errors.province = 'Province is required'
    }
    if (!values.district.trim()) {
      errors.district = 'District is required'
    } else if (values.district.length > 100) {
      errors.district = 'District must be at most 100 characters'
    }
    if (!values.category.trim()) {
      errors.category = 'Category is required'
    } else if (values.category.length > 100) {
      errors.category = 'Category must be at most 100 characters'
    }
    if (values.bestTimeToVisit && values.bestTimeToVisit.length > 200) {
      errors.bestTimeToVisit = 'Best time to visit must be at most 200 characters'
    }
    if (values.openingHours && values.openingHours.length > 300) {
      errors.openingHours = 'Opening hours must be at most 300 characters'
    }
    if (values.entryFee != null && values.entryFee < 0) {
      errors.entryFee = 'Entry fee cannot be negative'
    }
    if (values.visitorRules && values.visitorRules.length > 4000) {
      errors.visitorRules = 'Visitor rules must be at most 4000 characters'
    }
    const hasLat = values.latitude != null
    const hasLng = values.longitude != null
    if (hasLat !== hasLng) {
      errors[hasLat ? 'longitude' : 'latitude'] = 'Latitude and longitude must be entered together'
    }
    if (hasLat && (values.latitude! < -90 || values.latitude! > 90)) {
      errors.latitude = 'Latitude must be between -90 and 90'
    }
    if (hasLng && (values.longitude! < -180 || values.longitude! > 180)) {
      errors.longitude = 'Longitude must be between -180 and 180'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const numberOrNull = (raw: string): number | null => (raw.trim() === '' ? null : Number(raw))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await onSubmit({
        ...values,
        description: values.description?.trim() || undefined,
        bestTimeToVisit: values.bestTimeToVisit?.trim() || undefined,
        openingHours: values.openingHours?.trim() || undefined,
        visitorRules: values.visitorRules?.trim() || undefined,
        entryFee: values.entryFee ?? undefined,
        latitude: values.latitude ?? undefined,
        longitude: values.longitude ?? undefined,
        imageUrls: (values.imageUrls ?? []).map((url) => url.trim()).filter((url) => url.length > 0),
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

  const imageUrls = values.imageUrls ?? []

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
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input
          id="name"
          label="Name"
          value={values.name}
          onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
          error={fieldErrors.name}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Select
          id="province"
          label="Province"
          value={values.province}
          onChange={(e) =>
            setValues((v) => ({ ...v, province: e.target.value as DestinationRequestDto['province'], district: '' }))
          }
          error={fieldErrors.province}
        >
          <option value="">Select a province</option>
          {PROVINCES.map((province) => (
            <option key={province} value={province}>
              {formatProvince(province)}
            </option>
          ))}
        </Select>
        <Select
          id="district"
          label="District"
          value={values.district}
          onChange={(e) => setValues((v) => ({ ...v, district: e.target.value }))}
          error={fieldErrors.district}
          disabled={!values.province}
        >
          <option value="">{values.province ? 'Select a district' : 'Select a province first'}</option>
          {districtOptions.map((district) => (
            <option key={district} value={district}>
              {district}
            </option>
          ))}
        </Select>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Combobox
          id="category"
          label="Category"
          placeholder="e.g. Beach, Wildlife, Historical"
          value={values.category}
          onChange={(e) => setValues((v) => ({ ...v, category: e.target.value }))}
          error={fieldErrors.category}
          options={categoryOptions}
        />
        <Input
          id="bestTimeToVisit"
          label="Best Time to Visit (optional)"
          placeholder="e.g. December to April"
          value={values.bestTimeToVisit ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, bestTimeToVisit: e.target.value }))}
          error={fieldErrors.bestTimeToVisit}
        />
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

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input
          id="openingHours"
          label="Opening hours (optional)"
          placeholder="e.g. Daily 6am-6pm"
          value={values.openingHours ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, openingHours: e.target.value }))}
          error={fieldErrors.openingHours}
        />
        <Input
          id="entryFee"
          label="Entry fee (optional, 0 = free)"
          type="number"
          min={0}
          step="0.01"
          value={values.entryFee ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, entryFee: numberOrNull(e.target.value) }))}
          error={fieldErrors.entryFee}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="visitorRules" className="text-sm font-medium text-slate-700">
          Visitor rules &amp; guidelines (optional)
        </label>
        <textarea
          id="visitorRules"
          rows={3}
          placeholder="Dress code, photography, safety..."
          value={values.visitorRules ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, visitorRules: e.target.value }))}
          className={`rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            fieldErrors.visitorRules ? 'border-red-500' : 'border-slate-300'
          }`}
        />
        {fieldErrors.visitorRules && <span className="text-sm text-red-600">{fieldErrors.visitorRules}</span>}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input
          id="latitude"
          label="Latitude (optional)"
          type="number"
          step="any"
          min={-90}
          max={90}
          placeholder="e.g. 6.8667"
          value={values.latitude ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, latitude: numberOrNull(e.target.value) }))}
          error={fieldErrors.latitude}
        />
        <Input
          id="longitude"
          label="Longitude (optional)"
          type="number"
          step="any"
          min={-180}
          max={180}
          placeholder="e.g. 81.0466"
          value={values.longitude ?? ''}
          onChange={(e) => setValues((v) => ({ ...v, longitude: numberOrNull(e.target.value) }))}
          error={fieldErrors.longitude}
        />
      </div>

      <div className="flex flex-col gap-2">
        <span className="text-sm font-medium text-slate-700">Images (optional)</span>
        {imageUrls.map((url, index) => (
          <div key={index} className="flex gap-2">
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
          <Button type="button" variant="secondary" onClick={handleImageAdd}>
            Add Image URL
          </Button>
        </div>
      </div>

      {!initialValues && (
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={values.saveAsDraft ?? false}
            onChange={(e) => setValues((v) => ({ ...v, saveAsDraft: e.target.checked }))}
          />
          Save as draft (not visible to the public until published)
        </label>
      )}
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
