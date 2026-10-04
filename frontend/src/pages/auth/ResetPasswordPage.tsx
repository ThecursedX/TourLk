import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { resetPasswordRequest } from '../../api/authApi'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'

interface FormValues {
  password: string
  confirmPassword: string
}

export default function ResetPasswordPage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)

  const [values, setValues] = useState<FormValues>({ password: '', confirmPassword: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.password) {
      errors.password = 'Password is required'
    } else if (values.password.length < 8) {
      errors.password = 'Password must be at least 8 characters'
    }
    if (values.confirmPassword !== values.password) {
      errors.confirmPassword = 'Passwords do not match'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!token || !validate()) return

    setSubmitting(true)
    try {
      const res = await resetPasswordRequest({ token, newPassword: values.password })
      setSession(res)
      navigate(res.role === 'TOURIST' ? '/' : '/dashboard', { replace: true })
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
    <div className="flex flex-col items-center gap-6 py-8">
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 font-display text-xl font-bold text-slate-900">Choose a new password</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <Input
            id="password"
            label="New Password"
            type="password"
            value={values.password}
            onChange={(e) => setValues((v) => ({ ...v, password: e.target.value }))}
            error={fieldErrors.password}
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
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Resetting...' : 'Reset password'}
          </Button>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          <Link to="/login" className="font-medium text-blue-600 hover:underline">
            Back to login
          </Link>
        </p>
      </Card>
    </div>
  )
}
