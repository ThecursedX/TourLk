import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { changePassword, getMyProfile, submitLicence, updateMyProfile } from '../../api/userApi'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'
import LicenceDocumentLink from '../../components/users/LicenceDocumentLink'
import { formatLocalDate } from '../../utils/date'
import { LICENCE_ALLOWED_TYPES, LICENCE_MAX_BYTES, type UpdateProfileRequestDto, type UserResponseDto } from '../../types/user'

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
  const [licenceNumber, setLicenceNumber] = useState(profile.licenceNumber ?? '')
  const [licenceExpiry, setLicenceExpiry] = useState(profile.licenceExpiry ?? '')
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [renewing, setRenewing] = useState(false)

  const noun = profile.role === 'DRIVER' ? 'register a vehicle' : 'create a tour package'
  const status = profile.verificationStatus
  const days = profile.licenceDaysUntilExpiry
  const expiringSoon = status === 'VERIFIED' && !profile.licenceExpired && days !== null && days <= 30
  const canRenew = status === 'VERIFIED' && (profile.licenceExpired || expiringSoon)
  const showForm = status === 'NOT_SUBMITTED' || status === 'REJECTED' || (canRenew && renewing)
  const submitLabel = status === 'REJECTED' ? 'Resubmit' : status === 'VERIFIED' ? 'Submit renewal' : 'Submit for Review'

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const chosen = e.target.files?.[0] ?? null
    setFileError(null)
    if (!chosen) {
      setFile(null)
      return
    }
    if (!LICENCE_ALLOWED_TYPES.includes(chosen.type)) {
      setFile(null)
      setFileError('Only JPEG, PNG, WebP or PDF files are allowed.')
      e.target.value = ''
      return
    }
    if (chosen.size > LICENCE_MAX_BYTES) {
      setFile(null)
      setFileError('The file is larger than 5 MB.')
      e.target.value = ''
      return
    }
    setFile(chosen)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)
    if (!file) {
      setFileError('Please choose a licence document (JPEG, PNG, WebP or PDF, max 5 MB).')
      return
    }
    setSubmitting(true)
    try {
      const updated = await submitLicence({ licenceNumber, licenceExpiry, file })
      onUpdated(updated)
      setFile(null)
      setRenewing(false)
      setSuccess('Your licence has been submitted for review.')
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        const fieldErrors = err.response.data.fieldErrors
        const firstField = fieldErrors ? Object.values(fieldErrors)[0] : undefined
        setError(firstField ?? err.response.data.message)
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
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_BADGE[status]}`}>
          {STATUS_LABEL[status]}
        </span>
      </div>

      <p className="mb-4 text-sm text-slate-600">
        You must be a verified {profile.role === 'DRIVER' ? 'driver' : 'guide'} before you can {noun}.
      </p>

      {status === 'REJECTED' && profile.licenceRejectionReason && (
        <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          Rejected: {profile.licenceRejectionReason}
        </p>
      )}

      {status === 'PENDING' && <p className="text-sm text-slate-600">Your licence is awaiting admin review.</p>}

      {status === 'VERIFIED' && (
        <div className="flex flex-col gap-3">
          {profile.licenceExpiry && !profile.licenceExpired && (
            <p className="text-sm font-medium text-slate-700">Valid until {formatLocalDate(profile.licenceExpiry)}</p>
          )}
          {profile.licenceExpired && profile.licenceExpiry && (
            <p className="rounded-lg bg-red-50 p-3 text-sm font-medium text-red-700">
              Licence expired on {formatLocalDate(profile.licenceExpiry)}
            </p>
          )}
          {expiringSoon && (
            <p className="rounded-lg bg-amber-50 p-3 text-sm font-medium text-amber-700">
              Expires in {days} {days === 1 ? 'day' : 'days'}
            </p>
          )}
          <dl className="flex flex-col gap-1 text-sm text-slate-700">
            <div className="flex justify-between gap-2">
              <dt className="text-slate-500">Licence No.</dt>
              <dd>{profile.licenceNumber}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-slate-500">Expiry date</dt>
              <dd>{profile.licenceExpiry ? formatLocalDate(profile.licenceExpiry) : '-'}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-slate-500">Verified on</dt>
              <dd>{profile.licenceVerifiedAt ? new Date(profile.licenceVerifiedAt).toLocaleDateString() : '-'}</dd>
            </div>
          </dl>
          <div className="flex flex-wrap items-start gap-2">
            <LicenceDocumentLink user={profile} asButton />
            {canRenew && !renewing && (
              <Button type="button" onClick={() => setRenewing(true)}>
                Renew licence
              </Button>
            )}
          </div>
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="mt-1 flex flex-col gap-4" noValidate>
          <Input
            id="licenceNumber"
            label="Licence Number"
            value={licenceNumber}
            onChange={(e) => setLicenceNumber(e.target.value)}
          />
          <Input
            id="licenceExpiry"
            label="Licence Expiry"
            type="date"
            value={licenceExpiry}
            onChange={(e) => setLicenceExpiry(e.target.value)}
          />
          <div className="flex flex-col gap-1">
            <label htmlFor="licenceDocument" className="text-sm font-medium text-slate-700">
              Licence Document
            </label>
            <input
              id="licenceDocument"
              type="file"
              accept="image/jpeg,image/png,image/webp,application/pdf"
              onChange={handleFileChange}
              className="text-sm text-slate-700 file:mr-3 file:rounded-full file:border file:border-cobalt-200 file:bg-white/70 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-cobalt-700"
            />
            <span className="text-xs text-slate-500">JPEG, PNG, WebP or PDF, max 5 MB.</span>
            {file && <span className="text-sm text-slate-700">Selected: {file.name}</span>}
            {fileError && <span className="text-sm text-red-600">{fileError}</span>}
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex gap-2">
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Submitting...' : submitLabel}
            </Button>
            {status === 'VERIFIED' && (
              <Button type="button" variant="ghost" disabled={submitting} onClick={() => setRenewing(false)}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      )}

      {success && <p className="mt-3 text-sm text-green-700">{success}</p>}
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
