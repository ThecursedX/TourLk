import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { changePassword, getMyProfile, submitLicence, updateMyProfile } from '../../api/userApi'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'
import type { LicenceSubmitRequestDto, UpdateProfileRequestDto, UserResponseDto } from '../../types/user'

const emptyValues: UpdateProfileRequestDto = { name: '', email: '', phone: '' }

const STATUS_BADGE: Record<UserResponseDto['verificationStatus'], string> = {
  NOT_SUBMITTED: 'bg-slate-100 text-slate-700',
  PENDING: 'bg-amber-50 text-amber-700',
  VERIFIED: 'bg-green-50 text-green-700',
  REJECTED: 'bg-red-50 text-red-700',
}

const STATUS_LABEL: Record<UserResponseDto['verificationStatus'], string> = {
  NOT_SUBMITTED: 'Not Submitted',
  PENDING: 'Pending Review',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
}

function LicenceSection({ profile, onUpdated }: { profile: UserResponseDto; onUpdated: (p: UserResponseDto) => void }) {
  const [values, setValues] = useState<LicenceSubmitRequestDto>({
    licenceNumber: profile.licenceNumber ?? '',
    licenceExpiry: profile.licenceExpiry ?? '',
    licenceDocumentUrl: profile.licenceDocumentUrl ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const noun = profile.role === 'DRIVER' ? 'register a vehicle' : 'create a tour package'

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)
    setSubmitting(true)
    try {
      const updated = await submitLicence(values)
      onUpdated(updated)
      setSuccess('Your licence has been submitted for review.')
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setError(err.response.data.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="max-w-lg">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-display text-lg font-bold text-slate-900">Licence Verification</h2>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_BADGE[profile.verificationStatus]}`}>
          {STATUS_LABEL[profile.verificationStatus]}
        </span>
      </div>

      <p className="mb-4 text-sm text-slate-600">
        You must be a verified {profile.role === 'DRIVER' ? 'driver' : 'guide'} before you can {noun}.
      </p>

      {profile.verificationStatus === 'REJECTED' && profile.licenceRejectionReason && (
        <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          Rejected: {profile.licenceRejectionReason}
        </p>
      )}

      {profile.verificationStatus === 'PENDING' ? (
        <p className="text-sm text-slate-600">Your licence is awaiting admin review.</p>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <Input
            id="licenceNumber"
            label="Licence Number"
            value={values.licenceNumber}
            onChange={(e) => setValues((v) => ({ ...v, licenceNumber: e.target.value }))}
          />
          <Input
            id="licenceExpiry"
            label="Licence Expiry"
            type="date"
            value={values.licenceExpiry}
            onChange={(e) => setValues((v) => ({ ...v, licenceExpiry: e.target.value }))}
          />
          <Input
            id="licenceDocumentUrl"
            label="Licence Document URL"
            value={values.licenceDocumentUrl}
            onChange={(e) => setValues((v) => ({ ...v, licenceDocumentUrl: e.target.value }))}
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          {success && <p className="text-sm text-green-700">{success}</p>}
          <div>
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Submitting...' : profile.verificationStatus === 'REJECTED' ? 'Resubmit' : 'Submit for Review'}
            </Button>
          </div>
        </form>
      )}
    </Card>
  )
}

interface PasswordFormValues {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

const emptyPasswordValues: PasswordFormValues = { currentPassword: '', newPassword: '', confirmPassword: '' }

function ChangePasswordSection() {
  const [values, setValues] = useState<PasswordFormValues>(emptyPasswordValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.currentPassword) {
      errors.currentPassword = 'Current password is required'
    }
    if (!values.newPassword) {
      errors.newPassword = 'New password is required'
    } else if (values.newPassword.length < 8) {
      errors.newPassword = 'Password must be at least 8 characters'
    }
    if (values.confirmPassword !== values.newPassword) {
      errors.confirmPassword = 'Passwords do not match'
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
      await changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword })
      setValues(emptyPasswordValues)
      setSuccessMessage('Your password has been changed.')
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
    <Card className="max-w-lg">
      <h2 className="mb-4 font-display text-lg font-bold text-slate-900">Change Password</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Input
          id="currentPassword"
          label="Current Password"
          type="password"
          value={values.currentPassword}
          onChange={(e) => setValues((v) => ({ ...v, currentPassword: e.target.value }))}
          error={fieldErrors.currentPassword}
        />
        <Input
          id="newPassword"
          label="New Password"
          type="password"
          value={values.newPassword}
          onChange={(e) => setValues((v) => ({ ...v, newPassword: e.target.value }))}
          error={fieldErrors.newPassword}
        />
        <Input
          id="confirmPassword"
          label="Confirm New Password"
          type="password"
          value={values.confirmPassword}
          onChange={(e) => setValues((v) => ({ ...v, confirmPassword: e.target.value }))}
          error={fieldErrors.confirmPassword}
        />
        {formError && <p className="text-sm text-red-600">{formError}</p>}
        {successMessage && <p className="text-sm text-green-700">{successMessage}</p>}
        <div>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Saving...' : 'Change Password'}
          </Button>
        </div>
      </form>
    </Card>
  )
}

/**
 * Just account editing — every role-specific "my/mine" list now lives in the
 * left sidebar (see AccountLayout) instead of being aggregated here.
 */
export default function ProfilePage() {
  const setSession = useAuthStore((state) => state.setSession)
  const [values, setValues] = useState<UpdateProfileRequestDto>(emptyValues)
  const [profile, setProfile] = useState<UserResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getMyProfile()
      .then((p) => {
        setValues({ name: p.name, email: p.email, phone: p.phone ?? '' })
        setProfile(p)
      })
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

      {!loading && !loadError && profile && (profile.role === 'GUIDE' || profile.role === 'DRIVER') && (
        <LicenceSection profile={profile} onUpdated={setProfile} />
      )}

      {!loading && !loadError && <ChangePasswordSection />}
    </div>
  )
}
