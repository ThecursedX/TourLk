import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { getMyProfile, updateMyProfile } from '../../api/userApi'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'
import type { UpdateProfileRequestDto } from '../../types/user'

const emptyValues: UpdateProfileRequestDto = { name: '', email: '', phone: '' }

/**
 * Just account editing — every role-specific "my/mine" list now lives in the
 * left sidebar (see AccountLayout) instead of being aggregated here.
 */
export default function ProfilePage() {
  const setSession = useAuthStore((state) => state.setSession)
  const [values, setValues] = useState<UpdateProfileRequestDto>(emptyValues)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getMyProfile()
      .then((profile) =>
        setValues({ name: profile.name, email: profile.email, phone: profile.phone ?? '' }),
      )
      .catch(() => setLoadError('Could not load your profile. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 150) {
      errors.name = 'Name must be at most 150 characters'
    }
    if (!values.email.trim()) {
      errors.email = 'Email is required'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    setSuccessMessage(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      const res = await updateMyProfile({ ...values, phone: values.phone?.trim() || undefined })
      setSession(res)
      setSuccessMessage('Your profile has been updated.')
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
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Edit Profile</h1>
        <p className="mt-1 text-slate-600">Update your account details.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {loadError && <p className="text-red-600">{loadError}</p>}

      {!loading && !loadError && (
        <Card className="max-w-lg">
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <Input
              id="name"
              label="Name"
              value={values.name}
              onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
              error={fieldErrors.name}
            />
            <Input
              id="email"
              label="Email"
              type="email"
              value={values.email}
              onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))}
              error={fieldErrors.email}
            />
            <Input
              id="phone"
              label="Phone (optional)"
              value={values.phone ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
              error={fieldErrors.phone}
            />
            {formError && <p className="text-sm text-red-600">{formError}</p>}
            {successMessage && <p className="text-sm text-green-700">{successMessage}</p>}
            <div>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Saving...' : 'Save Changes'}
              </Button>
            </div>
          </form>
        </Card>
      )}
    </div>
  )
}
